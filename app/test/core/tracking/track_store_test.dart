import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/tracking/track_point.dart';
import 'package:running/core/tracking/track_store.dart';

void main() {
  late Directory tmp;

  setUp(() async {
    tmp = await Directory.systemTemp.createTemp('track_store_test');
  });

  tearDown(() async {
    await tmp.delete(recursive: true);
  });

  TrackPoint point(int sec) => TrackPoint(
        timestamp: DateTime.utc(2026, 7, 4, 8, 0, sec),
        lat: 37.5 + sec * 0.0001,
        lng: 127.0,
        altitude: 20,
        speed: 2.8,
        accuracy: 5.0,
      );

  test('append 후 readAll 하면 기록 순서와 값이 보존된다 (JSON 왕복)', () async {
    final store = TrackStore.forNewSession(tmp, DateTime.utc(2026, 7, 4, 8));
    await store.append(point(0));
    await store.append(point(4));
    await store.append(point(8));

    final points = await store.readAll();
    expect(points.length, 3);
    expect(points[0].timestamp, DateTime.utc(2026, 7, 4, 8, 0, 0));
    expect(points[2].timestamp, DateTime.utc(2026, 7, 4, 8, 0, 8));
    expect(points[1].lat, closeTo(37.5004, 1e-9));
    expect(points[1].speed, 2.8);
    expect(points[1].accuracy, 5.0);
  });

  test('timestamp는 UTC로 직렬화된다 — GPS 시각 우선 원칙의 저장 규약', () async {
    final store = TrackStore.forNewSession(tmp, DateTime.utc(2026, 7, 4, 8));
    // 로컬 타임존 시각을 넣어도 저장·복원 후엔 UTC 기준으로 동일 시점이어야 한다.
    final local = DateTime(2026, 7, 4, 17, 0, 0); // KST 가정 시 08:00Z
    await store.append(TrackPoint(
      timestamp: local,
      lat: 37.5,
      lng: 127.0,
      altitude: 0,
      speed: 0,
      accuracy: 10,
    ));
    final restored = (await store.readAll()).single;
    expect(restored.timestamp.isUtc, isTrue);
    expect(restored.timestamp, local.toUtc());
  });

  test('빈 파일/파일 없음이면 빈 목록 (크래시 아님 — 강제 종료 복구 경로)', () async {
    final store = TrackStore(File('${tmp.path}/없는파일.jsonl'));
    expect(await store.readAll(), isEmpty);
  });

  test('forNewSession 파일명에 세션 시각이 포함된다', () {
    final store = TrackStore.forNewSession(tmp, DateTime.utc(2026, 7, 4, 8, 30, 15));
    expect(store.path, contains('spike_track_20260704_083015.jsonl'));
  });
}
