/// 리플레이 재생 컨트롤러 — **순수 상태 전이**(Timer·DateTime·IO 없음).
///
/// 위젯이 Ticker 로 실제 경과(ms)를 주입([advance])하면, 배속을 곱해 재생 위치를
/// 전진시킨다. 배속·시킹·일시정지·종단 자동정지 판정을 순수하게 담아 골든 테스트
/// 대상으로 둔다(60fps 애니메이션은 위젯이 이 상태를 매 프레임 렌더).
library;

/// 지원 배속(1x/2x/4x).
const List<double> kReplaySpeeds = [1.0, 2.0, 4.0];

/// 재생 상태(불변 값). 전이는 새 인스턴스 반환.
class ReplayPlayback {
  const ReplayPlayback({
    required this.durationMs,
    this.positionMs = 0,
    this.speed = 1.0,
    this.playing = false,
  });

  /// 전체 길이(스냅샷 duration_ms).
  final int durationMs;

  /// 현재 재생 위치(상대 시각). [0, durationMs] 로 항상 클램프.
  final int positionMs;
  final double speed;
  final bool playing;

  bool get atEnd => positionMs >= durationMs;

  /// [0,1] 진행 비율(슬라이더용). duration 0 이면 0.
  double get progress => durationMs <= 0 ? 0 : positionMs / durationMs;

  /// 실제 경과 [elapsedRealMs] 를 배속 반영해 전진. 재생 중일 때만 이동.
  /// 끝에 도달하면 duration 에 고정하고 **자동 일시정지**(playing=false).
  ReplayPlayback advance(int elapsedRealMs) {
    if (!playing || elapsedRealMs <= 0) return this;
    final next = positionMs + (elapsedRealMs * speed).round();
    if (next >= durationMs) {
      return _copy(positionMs: durationMs, playing: false);
    }
    return _copy(positionMs: next);
  }

  /// 슬라이더 드래그 시킹 — 위치를 [ms] 로(클램프). 재생 상태는 유지.
  ReplayPlayback seek(int ms) => _copy(positionMs: _clamp(ms));

  /// 재생/일시정지 토글. 끝에서 재생 누르면 처음부터 다시 시작.
  ReplayPlayback togglePlay() {
    if (!playing && atEnd) {
      return _copy(positionMs: 0, playing: true);
    }
    return _copy(playing: !playing);
  }

  ReplayPlayback play() =>
      atEnd ? _copy(positionMs: 0, playing: true) : _copy(playing: true);

  ReplayPlayback pause() => _copy(playing: false);

  /// 배속 변경(허용 목록 밖은 무시). 재생 위치·상태 유지.
  ReplayPlayback setSpeed(double s) =>
      kReplaySpeeds.contains(s) ? _copy(speed: s) : this;

  /// 다음 배속으로 순환(1→2→4→1).
  ReplayPlayback cycleSpeed() {
    final i = kReplaySpeeds.indexOf(speed);
    final next = kReplaySpeeds[(i < 0 ? 0 : (i + 1)) % kReplaySpeeds.length];
    return _copy(speed: next);
  }

  ReplayPlayback restart() => _copy(positionMs: 0, playing: true);

  int _clamp(int ms) => ms < 0 ? 0 : (ms > durationMs ? durationMs : ms);

  ReplayPlayback _copy({int? positionMs, double? speed, bool? playing}) =>
      ReplayPlayback(
        durationMs: durationMs,
        positionMs: _clamp(positionMs ?? this.positionMs),
        speed: speed ?? this.speed,
        playing: playing ?? this.playing,
      );
}
