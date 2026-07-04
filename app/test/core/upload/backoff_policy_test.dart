import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/upload/backoff_policy.dart';

void main() {
  group('BackoffPolicy.delayForAttempt', () {
    const policy = BackoffPolicy(
      baseDelay: Duration(seconds: 2),
      maxDelay: Duration(minutes: 5),
      multiplier: 2.0,
      maxAttempts: 10,
    );

    test('지수적으로 증가한다 (2,4,8,16초)', () {
      expect(policy.delayForAttempt(1), const Duration(seconds: 2));
      expect(policy.delayForAttempt(2), const Duration(seconds: 4));
      expect(policy.delayForAttempt(3), const Duration(seconds: 8));
      expect(policy.delayForAttempt(4), const Duration(seconds: 16));
    });

    test('maxDelay 상한을 넘지 않는다', () {
      expect(policy.delayForAttempt(20), const Duration(minutes: 5));
    });

    test('attempt < 1 은 zero', () {
      expect(policy.delayForAttempt(0), Duration.zero);
    });
  });

  group('BackoffPolicy.shouldRetry', () {
    const policy = BackoffPolicy(maxAttempts: 3);

    test('maxAttempts 미만이면 재시도', () {
      expect(policy.shouldRetry(0), isTrue);
      expect(policy.shouldRetry(2), isTrue);
    });

    test('maxAttempts 도달·초과면 중단', () {
      expect(policy.shouldRetry(3), isFalse);
      expect(policy.shouldRetry(4), isFalse);
    });

    test('maxAttempts=0 이면 첫 시도부터 재시도 없음', () {
      const noRetry = BackoffPolicy(maxAttempts: 0);
      expect(noRetry.shouldRetry(0), isFalse);
    });
  });

  group('BackoffPolicy.delayForAttempt 경계값', () {
    const policy = BackoffPolicy(
      baseDelay: Duration(seconds: 2),
      maxDelay: Duration(minutes: 5),
      multiplier: 2.0,
      maxAttempts: 100,
    );

    test('상한 캡 직전(attempt 8=256s)은 캡되지 않는다', () {
      // 2s * 2^7 = 256s < 300s(=5분) → 그대로.
      expect(policy.delayForAttempt(8), const Duration(seconds: 256));
    });

    test('상한 캡 직후(attempt 9=512s 계산)는 maxDelay 로 캡된다', () {
      // 2s * 2^8 = 512s >= 300s → 300s 로 캡.
      expect(policy.delayForAttempt(9), const Duration(minutes: 5));
    });

    test('multiplier=1.0 이면 매번 baseDelay 로 일정', () {
      const flat = BackoffPolicy(baseDelay: Duration(seconds: 3), multiplier: 1.0);
      expect(flat.delayForAttempt(1), const Duration(seconds: 3));
      expect(flat.delayForAttempt(5), const Duration(seconds: 3));
    });

    test('소수 multiplier(1.5)도 정확히 반영된다', () {
      const frac = BackoffPolicy(
        baseDelay: Duration(seconds: 2),
        multiplier: 1.5,
        maxDelay: Duration(minutes: 5),
      );
      // 2 * 1.5^0 = 2s, 2 * 1.5^1 = 3s, 2 * 1.5^2 = 4.5s.
      expect(frac.delayForAttempt(1), const Duration(seconds: 2));
      expect(frac.delayForAttempt(2), const Duration(seconds: 3));
      expect(frac.delayForAttempt(3), const Duration(milliseconds: 4500));
    });

    test('음수 attempt 도 zero (attempt < 1)', () {
      expect(policy.delayForAttempt(-1), Duration.zero);
    });
  });
}
