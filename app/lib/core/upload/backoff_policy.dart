/// 지수 백오프 재시도 정책 — 순수 함수(시계·랜덤 없음).
///
/// 업로드는 완주 후 비동기 + 지수 백오프 재시도다. "서버 다운 = 결과 지연이지
/// 유실이 아니다"의 재시도 축을 순수 로직으로 분리해 골든 테스트 대상으로 둔다.
/// 지터(랜덤)는 순수성을 깨므로 이 정책엔 넣지 않는다 — 필요 시 소비자가
/// 별도 주입한다.
class BackoffPolicy {
  const BackoffPolicy({
    this.baseDelay = const Duration(seconds: 2),
    this.maxDelay = const Duration(minutes: 5),
    this.multiplier = 2.0,
    this.maxAttempts = 10,
  });

  /// 첫 재시도 지연.
  final Duration baseDelay;

  /// 지연 상한(무한 증가 방지).
  final Duration maxDelay;

  /// 시도마다 곱해지는 배수.
  final double multiplier;

  /// 이 횟수까지만 재시도(초과 시 포기 후보 — 로컬 데이터는 삭제하지 않는다).
  final int maxAttempts;

  /// [attempt](1-base)번째 재시도 전 대기 시간.
  ///
  /// attempt=1 → baseDelay, attempt=2 → baseDelay*multiplier, … (maxDelay 상한).
  /// attempt<1 은 Duration.zero.
  Duration delayForAttempt(int attempt) {
    if (attempt < 1) return Duration.zero;
    final factor = _pow(multiplier, attempt - 1);
    final micros = (baseDelay.inMicroseconds * factor);
    if (micros >= maxDelay.inMicroseconds) return maxDelay;
    return Duration(microseconds: micros.round());
  }

  /// [attempt]회까지 시도했을 때 한 번 더 재시도할지.
  bool shouldRetry(int attempt) => attempt < maxAttempts;

  static double _pow(double base, int exp) {
    var result = 1.0;
    for (var i = 0; i < exp; i++) {
      result *= base;
    }
    return result;
  }
}
