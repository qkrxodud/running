import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/replay_dtos.dart';
import 'package:running/core/model/track_dtos.dart';

import '../../support/contract_fixtures.dart';

/// replay-api v0.1 DTO(스키마 v1) — 버전 게이트·append-only 호환·enum 폴백·
/// display_names 조인·DNF null·status 분기.
///
/// P26-2/C8·A10: `docs/contracts/fixtures/replay_snapshot_v1.json` 은 이제 **서버가 생성**
/// (`SharedContractFixtureTest` — 실 ReplayMerger·OvertakeCalculator + ReplaySnapshotResponse
/// 직렬화). 수기 픽스처를 **서버 실바이트 소비로 교체** — 서버 스키마 drift 시 이 테스트가 red(뷰어측 가드).
/// 값(프레임 수·추월 방향·finish_time_ms 생략)은 서버 픽스처의 authoritative 값에 맞춘다.
void main() {
  group('READY 스냅샷 파싱 (서버 생성 공유 픽스처)', () {
    late ReplaySnapshotResponse res;
    setUp(() => res = ReplaySnapshotResponse.fromJson(
        loadContractFixture('replay_snapshot_v1.json')));

    test('status READY · 버전 지원 · payload 존재', () {
      expect(res.status, ReplayStatus.ready);
      expect(res.isReady, isTrue);
      expect(res.schemaVersion, 1);
      expect(res.isVersionSupported, isTrue);
    });

    test('display_names 조인 맵 (탈퇴 러너 포함)', () {
      expect(res.displayName(7), '지현');
      expect(res.displayName(8), '탈퇴한 러너');
      expect(res.displayName(999), '러너 999'); // 미조인 폴백
    });

    test('참가자·프레임·세그먼트·추월 파싱', () {
      final p = res.payload!;
      expect(p.participants, hasLength(3));
      // 첫 참가자(user 7): 6프레임, seg0 페이스 232 → 버킷 0.
      final a = p.participants.firstWhere((x) => x.userId == 7);
      expect(a.frames, hasLength(6));
      expect(a.segments.first.colorBucket, 0);
      // 추월 1건: B(3)가 A(7)를 추월(서버 순수 함수 산정 — 부호 반전).
      expect(p.overtakes.single.passerUserId, 3);
      expect(p.overtakes.single.passedUserId, 7);
      expect(p.durationMs, greaterThan(0));
    });

    test('DNF 참가자: finish_time_ms 키 생략 → null · frames 보존(경로 표시)', () {
      final dnf = res.payload!.participants.firstWhere((p) => p.isDnf);
      expect(dnf.finishStatus, FinishStatus.dnf);
      expect(dnf.finishTimeMs, isNull,
          reason: '서버 NON_NULL 직렬화로 finish_time_ms 키 자체 부재 → 파서 null');
      expect(dnf.frames, isNotEmpty, reason: 'DNF도 뛴 만큼 경로 보존');
    });

    test('is_gap 프레임 식별(GPS 유실 구분) — 어느 참가자든 공백 프레임 존재', () {
      final gapFrames = res.payload!.participants
          .expand((p) => p.frames)
          .where((f) => f.isGap);
      expect(gapFrames, isNotEmpty);
    });
  });

  group('버전 게이트 (MAX_SUPPORTED=1) — 크래시 금지', () {
    test('schema_version 2 > MAX → 파싱은 하되 렌더 거부(isVersionSupported false)', () {
      final res = ReplaySnapshotResponse.fromJson({
        'status': 'READY',
        'schema_version': 2,
        'display_names': {'7': '지현'},
        'payload': {
          'schema_version': 2,
          'session_id': 91,
          'course': {
            'distance_m': 5000,
            'route_polyline': '',
            'start': {'lat': 37.5, 'lng': 127.0},
            'finish': {'lat': 37.5, 'lng': 127.0},
          },
          'duration_ms': 1000,
          'participants': [],
          'overtakes': [],
          // 미래 스키마의 미지 필드 — 무시돼야 함(append-only).
          'future_field': {'anything': 1},
        },
      });
      expect(res.status, ReplayStatus.ready);
      expect(res.payload, isNotNull, reason: '크래시 없이 파싱');
      expect(res.isVersionSupported, isFalse, reason: 'MAX 초과 → 렌더 거부');
      expect(kMaxSupportedSnapshotVersion, 1);
    });

    test('미지 최상위/프레임 필드는 무시(append-only 하위호환)', () {
      final res = ReplaySnapshotResponse.fromJson({
        'status': 'READY',
        'schema_version': 1,
        'unknown_top': 'ignored',
        'display_names': {'7': '지현'},
        'payload': {
          'schema_version': 1,
          'session_id': 91,
          'course': {
            'distance_m': 5000,
            'route_polyline': '',
            'start': {'lat': 37.5, 'lng': 127.0},
            'finish': {'lat': 37.5, 'lng': 127.0},
          },
          'duration_ms': 3000,
          'participants': [
            {
              'user_id': 7,
              'finish_status': 'FINISHED',
              'finish_time_ms': 3000,
              'frames': [
                {
                  't_ms': 0,
                  'lat': 37.5,
                  'lng': 127.0,
                  'cum_dist_m': 0,
                  'is_gap': false,
                  'heart_rate': 150, // 미래 필드 — 무시
                }
              ],
              'segments': [],
            }
          ],
          'overtakes': [],
        },
      });
      expect(res.isVersionSupported, isTrue);
      expect(res.payload!.participants.first.frames.first.tMs, 0);
    });
  });

  group('status 분기·enum 폴백', () {
    test('GENERATING → payload/display_names null', () {
      final res = ReplaySnapshotResponse.fromJson({
        'status': 'GENERATING',
        'schema_version': null,
        'display_names': null,
        'payload': null,
      });
      expect(res.isGenerating, isTrue);
      expect(res.isReady, isFalse);
      expect(res.payload, isNull);
    });

    test('FAILED → payload null', () {
      final res = ReplaySnapshotResponse.fromJson(
          {'status': 'FAILED', 'payload': null});
      expect(res.isFailed, isTrue);
    });

    test('미지 status → unknown 폴백(크래시 금지)', () {
      final res =
          ReplaySnapshotResponse.fromJson({'status': 'ARCHIVED'});
      expect(res.status, ReplayStatus.unknown);
      expect(res.isReady, isFalse);
    });

    test('status 값 집합 == {GENERATING, READY, FAILED}', () {
      expect(ReplayStatus.wireValues.keys.toSet(),
          {'GENERATING', 'READY', 'FAILED'});
    });
  });
}
