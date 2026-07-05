import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/replay_dtos.dart';
import 'package:running/core/replay/frame_sampler.dart';

/// 프레임 보간 골든 — 마커 위치의 프레임 간 선형 보간·경계(시작 전/종료 후)·is_gap.
void main() {
  ReplayFrame f(int t, double lat, double lng, {bool gap = false}) =>
      ReplayFrame(tMs: t, lat: lat, lng: lng, cumDistM: t ~/ 10, isGap: gap);

  final frames = [
    f(0, 0, 0),
    f(1000, 10, 20),
    f(2000, 30, 60, gap: true), // 1000~2000 구간이 유실(도착 프레임 is_gap)
  ];

  test('빈 프레임 → null', () {
    expect(ReplayFrameSampler.sampleAt(const [], 500), isNull);
  });

  test('시작 전/시작점 → 첫 프레임 위치, finished=false', () {
    final s = ReplayFrameSampler.sampleAt(frames, -100)!;
    expect(s.position.lat, 0);
    expect(s.position.lng, 0);
    expect(s.finished, isFalse);
  });

  test('중간 보간 — 정확히 절반 지점', () {
    final s = ReplayFrameSampler.sampleAt(frames, 500)!;
    expect(s.position.lat, closeTo(5, 1e-9)); // 0→10 의 절반
    expect(s.position.lng, closeTo(10, 1e-9)); // 0→20 의 절반
    expect(s.finished, isFalse);
    expect(s.isGap, isFalse, reason: '도착 프레임(1000) is_gap=false');
  });

  test('유실 구간 보간 — 도착 프레임 is_gap 반영', () {
    final s = ReplayFrameSampler.sampleAt(frames, 1500)!;
    expect(s.position.lat, closeTo(20, 1e-9)); // 10→30 의 절반
    expect(s.isGap, isTrue, reason: '1000~2000 구간은 유실 보간');
  });

  test('종료 후 → 마지막 프레임 고정, finished=true (완주/DNF 마커 정지)', () {
    final s = ReplayFrameSampler.sampleAt(frames, 9999)!;
    expect(s.position.lat, 30);
    expect(s.position.lng, 60);
    expect(s.finished, isTrue);
  });

  test('정확히 프레임 경계 시각', () {
    final s = ReplayFrameSampler.sampleAt(frames, 1000)!;
    expect(s.position.lat, 10);
    expect(s.position.lng, 20);
  });

  test('단일 프레임 — 항상 그 위치, tMs 이상이면 finished', () {
    final one = [f(0, 5, 5)];
    expect(ReplayFrameSampler.sampleAt(one, 0)!.finished, isTrue);
    expect(ReplayFrameSampler.sampleAt(one, 5000)!.position.lat, 5);
  });
}
