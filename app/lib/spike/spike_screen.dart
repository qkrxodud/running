import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:geolocator/geolocator.dart';

import 'tracking_task_handler.dart';

/// M1 트래킹 스파이크 화면.
///
/// 로그인·디자인 없이 시작/중지 + 수집 현황만 보여준다. 이 화면의 목적은
/// "화면 끈 채 1시간 이상 유실 없는 백그라운드 기록"이라는 프로젝트 성립
/// 조건의 검증이지 제품 UI가 아니다 (제품 화면은 1a 라임 디자인 기준으로 별도).
class SpikeScreen extends StatefulWidget {
  const SpikeScreen({super.key});

  @override
  State<SpikeScreen> createState() => _SpikeScreenState();
}

class _SpikeScreenState extends State<SpikeScreen> {
  bool _running = false;
  int _count = 0;
  String? _lastTs;
  double? _lastAcc;
  String? _file;
  String? _startedAt;
  String? _error;

  @override
  void initState() {
    super.initState();
    FlutterForegroundTask.addTaskDataCallback(_onTaskData);
  }

  @override
  void dispose() {
    FlutterForegroundTask.removeTaskDataCallback(_onTaskData);
    super.dispose();
  }

  void _onTaskData(Object data) {
    if (data is! Map) return;
    setState(() {
      _count = (data['count'] as int?) ?? _count;
      _lastTs = (data['lastTs'] as String?) ?? _lastTs;
      _lastAcc = (data['lastAcc'] as num?)?.toDouble() ?? _lastAcc;
      _file = (data['file'] as String?) ?? _file;
      _startedAt = (data['startedAt'] as String?) ?? _startedAt;
      _error = data['error'] as String?;
    });
  }

  Future<bool> _ensurePermissions() async {
    // 위치 권한: 포그라운드 서비스 방식이므로 "앱 사용 중" 권한이면 충분하다.
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    if (permission == LocationPermission.denied ||
        permission == LocationPermission.deniedForever) {
      _showMessage('위치 권한이 필요합니다. 설정에서 허용해 주세요.');
      return false;
    }
    if (!await Geolocator.isLocationServiceEnabled()) {
      _showMessage('기기 위치(GPS)를 켜 주세요.');
      return false;
    }
    // 알림 권한(Android 13+): 상시 기록 알림이 Foreground Service의 조건이다.
    final np = await FlutterForegroundTask.checkNotificationPermission();
    if (np != NotificationPermission.granted) {
      await FlutterForegroundTask.requestNotificationPermission();
    }
    // 배터리 최적화 예외: 삼성 앱 자동 종료 대응 (스파이크 검증 항목).
    if (!await FlutterForegroundTask.isIgnoringBatteryOptimizations) {
      await FlutterForegroundTask.requestIgnoreBatteryOptimization();
    }
    return true;
  }

  Future<void> _start() async {
    if (!await _ensurePermissions()) return;
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'tracking_spike',
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
    await FlutterForegroundTask.startService(
      serviceId: 1,
      notificationTitle: '러닝 기록 중',
      notificationText: '기록을 시작했습니다.',
      callback: startTrackingCallback,
    );
    setState(() {
      _running = true;
      _count = 0;
      _error = null;
    });
  }

  Future<void> _stop() async {
    await FlutterForegroundTask.stopService();
    setState(() => _running = false);
  }

  void _showMessage(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('트래킹 스파이크 (M1)')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _InfoRow('상태', _running ? '기록 중' : '대기'),
            _InfoRow('수집 포인트', '$_count'),
            _InfoRow('시작 시각(UTC)', _startedAt ?? '-'),
            _InfoRow('마지막 포인트(UTC)', _lastTs ?? '-'),
            _InfoRow('마지막 정확도',
                _lastAcc == null ? '-' : '${_lastAcc!.toStringAsFixed(1)} m'),
            _InfoRow('저장 파일', _file ?? '-'),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text('오류: $_error',
                    style: const TextStyle(color: Colors.red)),
              ),
            const Spacer(),
            const Text(
              '검증 절차: 시작 → 화면 끄고 주머니에 넣기 → 1시간 이상 유지 → '
              '중지 → 포인트 수·간격 확인 (기대: 4초 간격, 시간당 ~900포인트)',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _running ? _stop : _start,
              style: FilledButton.styleFrom(minimumSize: const Size(0, 56)),
              child: Text(_running ? '기록 중지' : '기록 시작'),
            ),
          ],
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
              width: 130,
              child: Text(label,
                  style: const TextStyle(fontWeight: FontWeight.w600))),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}
