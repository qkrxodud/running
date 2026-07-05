import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/replay/replay_controller.dart';

/// 재생 컨트롤러 골든 — 배속·시킹·일시정지·종단 자동정지(시각 주입).
void main() {
  ReplayPlayback base() => const ReplayPlayback(durationMs: 10000);

  group('전진(배속 반영, 시각 주입)', () {
    test('일시정지 상태는 advance 무시', () {
      final pb = base(); // playing=false
      expect(pb.advance(1000).positionMs, 0);
    });

    test('1x: 실경과 그대로 전진', () {
      final pb = base().play();
      expect(pb.advance(1000).positionMs, 1000);
    });

    test('2x: 실경과의 2배 전진', () {
      final pb = base().play().setSpeed(2.0);
      expect(pb.advance(1000).positionMs, 2000);
    });

    test('4x: 실경과의 4배 전진', () {
      final pb = base().play().setSpeed(4.0);
      expect(pb.advance(1000).positionMs, 4000);
    });

    test('끝 도달 시 duration 고정 + 자동 일시정지', () {
      final pb = base().play().setSpeed(4.0);
      final ended = pb.advance(5000); // 20000 > 10000
      expect(ended.positionMs, 10000);
      expect(ended.playing, isFalse, reason: '끝에서 자동 정지');
      expect(ended.atEnd, isTrue);
    });
  });

  group('시킹(드래그)', () {
    test('seek 클램프 [0, duration]', () {
      expect(base().seek(3000).positionMs, 3000);
      expect(base().seek(-500).positionMs, 0);
      expect(base().seek(99999).positionMs, 10000);
    });

    test('seek 은 재생 상태 유지', () {
      expect(base().play().seek(2000).playing, isTrue);
    });
  });

  group('재생/일시정지 토글', () {
    test('toggle: 정지↔재생', () {
      final playing = base().togglePlay();
      expect(playing.playing, isTrue);
      expect(playing.togglePlay().playing, isFalse);
    });

    test('끝에서 play/toggle 은 처음부터 재시작', () {
      final ended = base().play().setSpeed(4.0).advance(5000); // 끝·정지
      expect(ended.atEnd, isTrue);
      final replayed = ended.togglePlay();
      expect(replayed.positionMs, 0);
      expect(replayed.playing, isTrue);
    });
  });

  group('배속 변경', () {
    test('setSpeed 허용 목록만(밖은 무시)', () {
      expect(base().setSpeed(2.0).speed, 2.0);
      expect(base().setSpeed(3.0).speed, 1.0, reason: '3x 미지원 — 무시');
    });

    test('cycleSpeed 순환 1→2→4→1', () {
      var pb = base();
      expect(pb.speed, 1.0);
      pb = pb.cycleSpeed();
      expect(pb.speed, 2.0);
      pb = pb.cycleSpeed();
      expect(pb.speed, 4.0);
      pb = pb.cycleSpeed();
      expect(pb.speed, 1.0);
    });

    test('배속 변경은 재생 위치 유지', () {
      final pb = base().play().advance(1000).cycleSpeed();
      expect(pb.positionMs, 1000);
      expect(pb.speed, 2.0);
    });
  });

  test('progress = position/duration', () {
    expect(base().seek(2500).progress, 0.25);
    expect(const ReplayPlayback(durationMs: 0).progress, 0);
  });

  test('restart: 위치 0·재생', () {
    final pb = base().play().advance(1000).restart();
    expect(pb.positionMs, 0);
    expect(pb.playing, isTrue);
  });
}
