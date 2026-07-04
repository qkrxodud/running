import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/history_dtos.dart';
import 'package:running/core/model/page_response.dart';
import 'package:running/core/model/track_dtos.dart';

import '../../support/contract_fixtures.dart';

/// history-api.md v0.1 DTO — 키부재=null(P46-1)·avg_pace 필드명(P46-2)·
/// finish_status enum 재사용(track_dtos FinishStatus {FINISHED,DNF})·CANCELLED 배지.
///
/// P26-2/C8: RecordHistoryItem·PersonalBestItem 파싱은 **서버가 생성한 공유 픽스처**
/// (`docs/contracts/fixtures/`)를 로드해 검증한다(손수 만든 맵 아님 — 교차 CI drift 가드).
void main() {
  group('finish_status 값 집합 — track_dtos FinishStatus 재사용(신규 정의 없음)', () {
    test('history finish_status = {FINISHED, DNF} (DNS 부재 — 뛴 기록만)', () {
      // 히스토리는 track FinishStatus 를 재사용 — 계약 값집합 동일.
      expect(FinishStatus.wireValues.keys.toSet(), {'FINISHED', 'DNF'});
      // DNS 는 wire 에 없음(track_record 없어 히스토리 미노출).
      expect(FinishStatus.wireValues.keys, isNot(contains('DNS')));
    });

    test('미지 finish_status → unknown 폴백(크래시 금지)', () {
      expect(FinishStatus.parse('WEIRD'), FinishStatus.unknown);
    });
  });

  group('RecordHistoryItem 파싱 — 공유 픽스처(서버 실바이트 · 페이지 래퍼) 소비', () {
    // 서버 SharedContractFixtureTest 가 생성한 me/records 페이지 응답 그대로.
    final page = PageResponse.fromJson(
      loadContractFixture('history_records_mixed.json'),
      RecordHistoryItem.fromJson,
    );
    RecordHistoryItem byTrack(int id) =>
        page.items.firstWhere((i) => i.trackRecordId == id);

    test('페이지 래퍼 — items 3건·total_elements 3', () {
      expect(page.items.length, 3);
      expect(page.totalElements, 3);
      expect(page.size, 20);
    });

    test('완주 항목 — 전 필드 + is_pb + avg_pace_s_per_km(P46-2)', () {
      final r = byTrack(4021);
      expect(r.finishStatus, FinishStatus.finished);
      expect(r.rank, 1);
      expect(r.avgPaceSPerKm, 299,
          reason: 'avg_pace_s_per_km 필드명 일치(서버 @JsonProperty ↔ 앱 파서)');
      expect(r.isPb, isTrue);
      expect(r.canPromote, isTrue, reason: 'FINISHED → 승격 가능');
    });

    test('DNF 항목 — rank/record/pace 키 자체 생략(NON_NULL) → null(P46-1), 승격 불가', () {
      final r = byTrack(4102);
      expect(r.finishStatus, FinishStatus.dnf);
      expect(r.rank, isNull);
      expect(r.recordTimeS, isNull);
      expect(r.avgPaceSPerKm, isNull);
      expect(r.totalDistanceM, 3120);
      expect(r.canPromote, isFalse, reason: 'DNF → 승격 불가(PR-2)');
    });

    test('CANCELLED 세션 완주 항목 — 배지 true·rank 키 생략(null)·완주면 승격 가능', () {
      final r = byTrack(4150);
      expect(r.sessionCancelled, isTrue);
      expect(r.rank, isNull, reason: 'CANCELLED 세션 순위 미산정(rank 키 생략)');
      expect(r.isPb, isFalse);
      expect(r.canPromote, isTrue, reason: '취소 세션이어도 완주면 승격 가능(§4)');
    });
  });

  test('PersonalBestItem 파싱 — 공유 픽스처(페이지 래퍼) 소비', () {
    final page = PageResponse.fromJson(
      loadContractFixture('personal_bests.json'),
      PersonalBestItem.fromJson,
    );
    expect(page.items.length, greaterThanOrEqualTo(1));
    final pb = page.items.first;
    expect(pb.courseName, '한강 5K');
    expect(pb.bestRecordTimeS, 1502);
    expect(pb.avgPaceSPerKm, 299);
    expect(pb.achievedSessionId, 91);
  });
}
