import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/tracking/sampling_policy.dart';

void main() {
  const policy = SamplingPolicy();

  group('SamplingPolicy.intervalFor', () {
    test('주행 중이면 active 간격(4초)', () {
      expect(
        policy.intervalFor(speedMps: 3.0, stationaryFor: Duration.zero),
        const Duration(seconds: 4),
      );
    });

    test('정지가 유예(30초) 이상 지속되면 idle 간격(10초)으로 완화', () {
      expect(
        policy.intervalFor(
          speedMps: 0.1,
          stationaryFor: const Duration(seconds: 31),
        ),
        const Duration(seconds: 10),
      );
    });

    test('정지지만 유예 이내면 아직 active 간격', () {
      expect(
        policy.intervalFor(
          speedMps: 0.0,
          stationaryFor: const Duration(seconds: 10),
        ),
        const Duration(seconds: 4),
      );
    });

    test('완화 중이라도 다시 움직이면 즉시 active 로 복귀', () {
      expect(
        policy.intervalFor(
          speedMps: 2.5,
          stationaryFor: const Duration(minutes: 5),
        ),
        const Duration(seconds: 4),
      );
    });

    test('임계 속도 경계값(threshold)은 움직임으로 간주', () {
      expect(
        policy.intervalFor(
          speedMps: 0.5,
          stationaryFor: const Duration(seconds: 60),
        ),
        const Duration(seconds: 4),
      );
    });
  });

  group('SamplingPolicy.intervalFor 경계값', () {
    // 정지 판정: speed < 0.5 이면 정지. 완화 전환: stationaryFor >= 30s.

    test('임계 속도 직전(0.49)이고 유예 초과면 idle 로 완화', () {
      // 0.49 < 0.5 → 정지, 지속 31s >= 30s → idle.
      expect(
        policy.intervalFor(
          speedMps: 0.49,
          stationaryFor: const Duration(seconds: 31),
        ),
        const Duration(seconds: 10),
      );
    });

    test('임계 속도 직후(0.51)면 정지 지속과 무관하게 active 로 복귀', () {
      expect(
        policy.intervalFor(
          speedMps: 0.51,
          stationaryFor: const Duration(minutes: 10),
        ),
        const Duration(seconds: 4),
      );
    });

    test('정지 지속이 정확히 유예(30초)면 idle 로 전환 (>= 경계 포함)', () {
      expect(
        policy.intervalFor(
          speedMps: 0.0,
          stationaryFor: const Duration(seconds: 30),
        ),
        const Duration(seconds: 10),
      );
    });

    test('정지 지속이 유예 직전(29초)이면 아직 active', () {
      expect(
        policy.intervalFor(
          speedMps: 0.0,
          stationaryFor: const Duration(seconds: 29),
        ),
        const Duration(seconds: 4),
      );
    });

    test('정지 즉시(지속 0)면 유예 이내이므로 active 유지', () {
      expect(
        policy.intervalFor(speedMps: 0.0, stationaryFor: Duration.zero),
        const Duration(seconds: 4),
      );
    });
  });
}
