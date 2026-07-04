import 'dart:async';

import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:geolocator/geolocator.dart';

import '../../core/tracking/track_point.dart';
import '../notification/android_notification_service.dart';
import '../notification/notification_service.dart';
import 'location_tracker.dart';

/// [LocationTracker] 의 Android MVP 구현 — Foreground Service(type: location) +
/// 상시 알림. 스파이크(`spike/tracking_task_handler.dart`)의 geolocator +
/// flutter_foreground_task 로직을 인터페이스 뒤로 재배치한 것이다.
///
/// 구조: 트래킹은 Foreground Service isolate([_AndroidTrackerTaskHandler])에서
/// geolocator 스트림을 돌리고, 각 포인트를 main isolate 로 보낸다
/// (`sendDataToMain`). main isolate 의 이 트래커가 [TrackPoint] 로 복원해
/// [points] 로 방출한다. 알림 lifecycle 은 이 트래커가 소유하고, 내용 갱신은
/// [NotificationService] 에 위임한다.
///
/// Android 전용 import(geolocator, flutter_foreground_task)는 이 파일과 형제
/// 구현체에만 존재한다 — core/ 로 새지 않는다.
class AndroidForegroundTracker implements LocationTracker {
  AndroidForegroundTracker({NotificationService? notificationService})
      : _notifications =
            notificationService ?? const AndroidNotificationService();

  final NotificationService _notifications;

  final _pointsController = StreamController<TrackPoint>.broadcast();
  final _stateController = StreamController<TrackerState>.broadcast();

  TrackerState _state = TrackerState.ready;
  bool _callbackRegistered = false;

  @override
  TrackerState get state => _state;

  @override
  Stream<TrackerState> get stateChanges => _stateController.stream;

  @override
  Stream<TrackPoint> get points => _pointsController.stream;

  void _setState(TrackerState next) {
    if (_state == next) return;
    _state = next;
    if (!_stateController.isClosed) _stateController.add(next);
  }

  @override
  Future<void> start() async {
    if (_state == TrackerState.running || _state == TrackerState.paused) return;

    _initForegroundTask();
    if (!_callbackRegistered) {
      FlutterForegroundTask.addTaskDataCallback(_onTaskData);
      _callbackRegistered = true;
    }

    await FlutterForegroundTask.startService(
      serviceId: _serviceId,
      notificationTitle: '러닝 기록 중',
      notificationText: '기록을 시작했습니다.',
      callback: androidTrackerCallback,
    );
    _setState(TrackerState.running);
  }

  @override
  Future<void> pause() async {
    // 그로스 타임: 기록 시간은 계속 흐른다. pause 는 상태 훅일 뿐 서비스는 유지.
    if (_state == TrackerState.running) _setState(TrackerState.paused);
  }

  @override
  Future<void> resume() async {
    if (_state == TrackerState.paused) _setState(TrackerState.running);
  }

  @override
  Future<void> stop() async {
    await FlutterForegroundTask.stopService();
    await _notifications.stopTrackingNotification();
    _setState(TrackerState.stopped);
  }

  @override
  Future<void> dispose() async {
    if (_callbackRegistered) {
      FlutterForegroundTask.removeTaskDataCallback(_onTaskData);
      _callbackRegistered = false;
    }
    await _pointsController.close();
    await _stateController.close();
  }

  Future<void> _onTaskData(Object data) async {
    if (data is! Map) return;
    if (data['error'] != null) return; // 오류 핑은 무시(관측은 상위 레이어)
    if (!data.containsKey('ts')) return;
    final point = TrackPoint.fromJson(Map<String, dynamic>.from(data));
    if (!_pointsController.isClosed) _pointsController.add(point);
    // 저빈도 알림 갱신은 상위 레이어가 updateTrackingNotification 으로 수행.
  }

  void _initForegroundTask() {
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: _channelId,
        channelName: '러닝 기록',
        channelDescription: '레이스 중 위치를 기록합니다.',
        channelImportance: NotificationChannelImportance.LOW,
        priority: NotificationPriority.LOW,
      ),
      iosNotificationOptions: const IOSNotificationOptions(),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.nothing(),
        autoRunOnBoot: false,
        allowWakeLock: true,
        allowWifiLock: false,
      ),
    );
  }

  static const int _serviceId = 1;
  static const String _channelId = 'tracking';
}

/// Foreground Service isolate 진입점. main isolate 와 분리된 컨텍스트에서
/// geolocator 스트림을 돌린다.
@pragma('vm:entry-point')
void androidTrackerCallback() {
  FlutterForegroundTask.setTaskHandler(_AndroidTrackerTaskHandler());
}

/// 서비스 isolate 의 트래킹 핸들러 — geolocator 포인트를 [TrackPoint] 로 변환해
/// main isolate 로 전송한다. (로컬 append 파이프라인 배선은 배치 B.)
class _AndroidTrackerTaskHandler extends TaskHandler {
  StreamSubscription<Position>? _positionSub;

  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    // 3~5초 간격 목표: 기본 4초. 적응형 완화는 core/tracking/SamplingPolicy 소비로 배치 B.
    final settings = AndroidSettings(
      accuracy: LocationAccuracy.best,
      intervalDuration: const Duration(seconds: 4),
      distanceFilter: 0,
    );
    _positionSub =
        Geolocator.getPositionStream(locationSettings: settings).listen(
      (p) {
        final point = TrackPoint(
          timestamp: p.timestamp.toUtc(), // GPS 시각 우선
          lat: p.latitude,
          lng: p.longitude,
          altitude: p.altitude,
          speed: p.speed,
          accuracy: p.accuracy,
        );
        FlutterForegroundTask.sendDataToMain(point.toJson());
      },
      onError: (Object e) {
        FlutterForegroundTask.sendDataToMain({'error': e.toString()});
      },
    );
  }

  @override
  void onRepeatEvent(DateTime timestamp) {}

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {
    await _positionSub?.cancel();
    _positionSub = null;
  }
}
