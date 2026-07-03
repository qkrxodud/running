import 'dart:async';

import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:geolocator/geolocator.dart';
import 'package:path_provider/path_provider.dart';

import '../core/tracking/track_point.dart';
import '../core/tracking/track_store.dart';

/// Foreground Service 격리(isolate)에서 실행되는 트래킹 핸들러.
///
/// 스파이크 목적: 화면 꺼짐 상태에서 1시간 이상 3~5초 간격 기록이
/// 유실 없이 로컬 파일에 쌓이는지 검증한다. 적응형 샘플링(정지 시 완화)은
/// 스파이크 통과 후 M2에서 구현한다.
class TrackingTaskHandler extends TaskHandler {
  StreamSubscription<Position>? _positionSub;
  TrackStore? _store;
  int _count = 0;
  DateTime? _startedAt;

  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    _startedAt = DateTime.now().toUtc();
    final dir = await getApplicationDocumentsDirectory();
    _store = TrackStore.forNewSession(dir, _startedAt!);

    // 3~5초 간격 목표: intervalDuration 4초, 거리 필터 없음(시간 기준 수집).
    final settings = AndroidSettings(
      accuracy: LocationAccuracy.best,
      intervalDuration: Duration(seconds: 4),
      distanceFilter: 0,
    );
    _positionSub =
        Geolocator.getPositionStream(locationSettings: settings).listen(
      _onPosition,
      onError: (Object e) {
        FlutterForegroundTask.sendDataToMain({'error': e.toString()});
      },
    );
  }

  Future<void> _onPosition(Position p) async {
    final point = TrackPoint(
      // p.timestamp = 위치 프레임워크가 주는 fix 시각 (GPS 시각 우선 원칙)
      timestamp: p.timestamp.toUtc(),
      lat: p.latitude,
      lng: p.longitude,
      altitude: p.altitude,
      speed: p.speed,
      accuracy: p.accuracy,
    );
    await _store?.append(point); // 수신 즉시 로컬 append (로컬 우선)
    _count++;

    FlutterForegroundTask.sendDataToMain({
      'count': _count,
      'lastTs': point.timestamp.toIso8601String(),
      'lastAcc': point.accuracy,
      'file': _store?.path,
      'startedAt': _startedAt?.toIso8601String(),
    });
    FlutterForegroundTask.updateService(
      notificationTitle: '러닝 기록 중',
      notificationText: '$_count 포인트 · 정확도 ${point.accuracy.toStringAsFixed(0)}m',
    );
  }

  // eventAction은 nothing()을 쓰므로 반복 콜백은 상태 핑 용도로만 남긴다.
  @override
  void onRepeatEvent(DateTime timestamp) {}

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {
    await _positionSub?.cancel();
    _positionSub = null;
  }
}

@pragma('vm:entry-point')
void startTrackingCallback() {
  FlutterForegroundTask.setTaskHandler(TrackingTaskHandler());
}
