import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/tracking/track_buffer.dart';
import 'package:running/core/tracking/track_point.dart';

void main() {
  TrackPoint pt(int sec) => TrackPoint(
        timestamp: DateTime.utc(2026, 7, 4, 8, 0, sec),
        lat: 37.5,
        lng: 127.0,
        altitude: 0,
        speed: 2.0,
        accuracy: 5,
      );

  test('개수 임계 도달 시 flush 필요', () {
    final buffer = TrackBuffer(maxBufferedPoints: 3);
    final now = DateTime.utc(2026, 7, 4, 8);
    buffer.add(pt(0), now: now);
    buffer.add(pt(1), now: now);
    expect(buffer.shouldFlush(now), isFalse);
    buffer.add(pt(2), now: now);
    expect(buffer.shouldFlush(now), isTrue);
  });

  test('age 임계 초과 시 flush 필요 (시각 주입)', () {
    final buffer = TrackBuffer(
      maxBufferedPoints: 100,
      maxBufferAge: const Duration(seconds: 15),
    );
    final start = DateTime.utc(2026, 7, 4, 8);
    buffer.add(pt(0), now: start);
    expect(buffer.shouldFlush(start.add(const Duration(seconds: 14))), isFalse);
    expect(buffer.shouldFlush(start.add(const Duration(seconds: 15))), isTrue);
  });

  test('빈 버퍼는 flush 불필요', () {
    final buffer = TrackBuffer();
    expect(buffer.shouldFlush(DateTime.utc(2026, 7, 4, 8)), isFalse);
  });

  test('drain 은 내용을 순서대로 반환하고 버퍼를 비운다 (age 초기화)', () {
    final buffer = TrackBuffer(maxBufferedPoints: 2);
    final start = DateTime.utc(2026, 7, 4, 8);
    buffer.add(pt(0), now: start);
    buffer.add(pt(1), now: start);
    final drained = buffer.drain();
    expect(drained.map((p) => p.timestamp.second), [0, 1]);
    expect(buffer.isEmpty, isTrue);
    // drain 후 age 기준 초기화 — 새 포인트의 now 가 기준이 된다.
    buffer.add(pt(2), now: start.add(const Duration(seconds: 20)));
    expect(buffer.shouldFlush(start.add(const Duration(seconds: 21))), isFalse);
  });

  group('TrackBuffer 경계값', () {
    final start = DateTime.utc(2026, 7, 4, 8);

    test('빈 버퍼 drain 은 빈 목록을 반환하고 이후에도 flush 불필요', () {
      final buffer = TrackBuffer();
      expect(buffer.drain(), isEmpty);
      expect(buffer.isEmpty, isTrue);
      expect(buffer.shouldFlush(start), isFalse);
    });

    test('정확히 임계 개수-1 까지는 flush 불필요, 임계 도달 시 flush 필요', () {
      final buffer = TrackBuffer(maxBufferedPoints: 2, maxBufferAge: const Duration(hours: 1));
      buffer.add(pt(0), now: start);
      expect(buffer.length, 1);
      expect(buffer.shouldFlush(start), isFalse); // 임계-1
      buffer.add(pt(1), now: start);
      expect(buffer.shouldFlush(start), isTrue); // 정확히 임계
    });

    test('age 는 최초 진입 포인트 기준 — 이후 add 로 갱신되지 않는다', () {
      final buffer = TrackBuffer(
        maxBufferedPoints: 100,
        maxBufferAge: const Duration(seconds: 15),
      );
      buffer.add(pt(0), now: start); // 최초 진입 = start
      buffer.add(pt(5), now: start.add(const Duration(seconds: 10)));
      // 최초 진입 기준 15초 → start+15 에서 flush. 두번째 add 가 기준을 늦추지 않음.
      expect(buffer.shouldFlush(start.add(const Duration(seconds: 15))), isTrue);
    });

    test('drain 반환 목록은 수정 불가(불변)', () {
      final buffer = TrackBuffer();
      buffer.add(pt(0), now: start);
      final drained = buffer.drain();
      expect(() => drained.add(pt(1)), throwsUnsupportedError);
    });

    test('drain 후 shouldFlush 는 다시 false (상태 초기화)', () {
      final buffer = TrackBuffer(maxBufferedPoints: 2);
      buffer.add(pt(0), now: start);
      buffer.add(pt(1), now: start);
      expect(buffer.shouldFlush(start), isTrue);
      buffer.drain();
      expect(buffer.shouldFlush(start), isFalse);
    });
  });
}
