import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/version/force_update_policy.dart';

/// 강제 업데이트 판단 (B1-C3). 순수 semver 비교 — 가용성 우선(판단 불가는 통과).
void main() {
  bool required(String current, String min) =>
      ForceUpdatePolicy.isUpdateRequired(
        currentVersion: current,
        minVersion: min,
      );

  group('semver 비교', () {
    test('current < min → 업데이트 필요', () {
      expect(required('1.1.0', '1.2.0'), isTrue);
      expect(required('1.2.0', '1.2.1'), isTrue);
      expect(required('0.9.9', '1.0.0'), isTrue);
    });

    test('current >= min → 불요', () {
      expect(required('1.2.0', '1.2.0'), isFalse);
      expect(required('1.3.0', '1.2.0'), isFalse);
      expect(required('2.0.0', '1.9.9'), isFalse);
    });

    test('누락 세그먼트는 0 취급 (1.2 == 1.2.0)', () {
      expect(required('1.2', '1.2.0'), isFalse);
      expect(required('1.2', '1.2.1'), isTrue);
    });

    test('빌드 넘버(+45)는 무시', () {
      expect(required('1.2.0+45', '1.2.0'), isFalse);
    });
  });

  group('가용성 우선 — 파싱 불가는 false (앱을 잠그지 않는다)', () {
    test('비정상 문자열은 업데이트 불요', () {
      expect(required('abc', '1.2.0'), isFalse);
      expect(required('1.2.0', 'not-a-version'), isFalse);
      expect(required('', '1.2.0'), isFalse);
      expect(required('1.2.3.4', '1.2.0'), isFalse); // 세그먼트 초과
    });
  });
}
