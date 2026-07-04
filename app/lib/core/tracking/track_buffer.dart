import 'track_point.dart';

/// 트랙 포인트 메모리 버퍼 + 주기 flush 판단 — 순수 로직(IO 없음, 시각 주입).
///
/// 로컬 우선 원칙: 포인트는 수신 즉시 로컬에 append 되어야 하되, 매 포인트
/// 디스크 flush는 배터리·IO 비용이 크다. 버퍼는 "언제 flush 할지"만 순수
/// 로직으로 판단하고, 실제 파일 쓰기는 저장 모듈(core/storage 또는 TrackStore)이
/// 담당한다. 이 클래스는 시계에 의존하지 않도록 now 를 인자로 받는다.
///
/// 유실 경계: [shouldFlush] 가 true 가 되는 즉시 소비자가 [drain] 해 저장하면,
/// 앱 강제 종료 시 최대 [maxBufferedPoints]-1 개(또는 [maxBufferAge] 이내) 만
/// 위험 구간이다. 임계값은 유실 허용치 대비 튜닝 대상.
class TrackBuffer {
  TrackBuffer({
    this.maxBufferedPoints = 20,
    this.maxBufferAge = const Duration(seconds: 15),
  });

  /// 이 개수 이상 쌓이면 flush 필요.
  final int maxBufferedPoints;

  /// 가장 오래된 포인트가 이 시간 이상 버퍼에 머물면 flush 필요.
  final Duration maxBufferAge;

  final List<TrackPoint> _points = [];
  DateTime? _oldestAt;

  int get length => _points.length;
  bool get isEmpty => _points.isEmpty;

  /// 버퍼에 포인트 추가. 첫 포인트의 버퍼 진입 시각([now])을 age 기준으로 기록.
  void add(TrackPoint point, {required DateTime now}) {
    _points.add(point);
    _oldestAt ??= now;
  }

  /// 지금([now]) flush 해야 하는가 — 개수 초과 또는 최고령 포인트 age 초과.
  bool shouldFlush(DateTime now) {
    if (_points.isEmpty) return false;
    if (_points.length >= maxBufferedPoints) return true;
    final oldest = _oldestAt;
    return oldest != null && now.difference(oldest) >= maxBufferAge;
  }

  /// 버퍼 내용을 반환하고 비운다(소비자가 저장한다). age 기준도 초기화.
  List<TrackPoint> drain() {
    final drained = List<TrackPoint>.unmodifiable(_points);
    _points.clear();
    _oldestAt = null;
    return drained;
  }
}
