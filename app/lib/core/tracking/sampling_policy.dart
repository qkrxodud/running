/// 적응형 GPS 샘플링 간격 판단 — 순수 함수(시계·IO 없음, 시각은 인자 주입).
///
/// 배터리 절약: 주행 중에는 촘촘히(3~5초), 정지가 일정 시간 지속되면 완화(10초),
/// 다시 움직이면 즉시 복귀한다. 판단만 순수 Dart로 두고, 실제 간격 적용은
/// platform/ 트래커 구현체가 이 결과를 소비한다(테스트 대상 경계).
///
/// 주의: 이 완화는 "샘플링 빈도"만 조정한다. 그로스 타임 원칙상 기록 시간은
/// 계속 흐르며(자동 일시정지 아님), 정지 판정으로 기록을 잘라내지 않는다.
class SamplingPolicy {
  const SamplingPolicy({
    this.activeInterval = const Duration(seconds: 4),
    this.idleInterval = const Duration(seconds: 10),
    this.stationarySpeedThreshold = 0.5, // m/s 미만이면 정지로 간주
    this.stationaryGrace = const Duration(seconds: 30), // 정지 지속 유예
  });

  /// 주행 중 목표 간격(3~5초 중앙값 기본 4초).
  final Duration activeInterval;

  /// 정지 지속 시 완화 간격.
  final Duration idleInterval;

  /// 정지로 간주하는 속도 상한(m/s).
  final double stationarySpeedThreshold;

  /// 이 시간 이상 정지가 지속돼야 완화로 전환.
  final Duration stationaryGrace;

  /// 현재 속도와 "정지 지속 시간"으로 다음 샘플링 간격을 결정한다.
  ///
  /// - 움직이는 중(speed ≥ threshold): 즉시 [activeInterval] 로 복귀.
  /// - 정지가 [stationaryGrace] 이상 지속: [idleInterval].
  /// - 정지지만 유예 이내: 아직 [activeInterval].
  Duration intervalFor({
    required double speedMps,
    required Duration stationaryFor,
  }) {
    final moving = speedMps >= stationarySpeedThreshold;
    if (moving) return activeInterval;
    if (stationaryFor >= stationaryGrace) return idleInterval;
    return activeInterval;
  }
}
