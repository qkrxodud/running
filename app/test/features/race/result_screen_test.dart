import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/geo/lat_lng.dart';
import 'package:running/core/model/race_dtos.dart';
import 'package:running/core/model/track_dtos.dart';
import 'package:running/data/track_repository.dart';
import 'package:running/features/race/result_screen.dart';

import '../../support/fakes.dart';

RaceResultResponse _result() => RaceResultResponse(
      sessionId: 91,
      course: const ResultCourse(id: 55, name: '한강 5K', distanceM: 5000),
      finalizedAt: DateTime.utc(2026, 7, 11, 9),
      entries: const [
        // 동률 공동순위(1,1,3) — 서버 산정값 그대로.
        ResultEntry(
            userId: 3,
            nickname: '민수',
            status: ResultEntryStatus.finished,
            rank: 1,
            recordTimeS: 1502,
            totalDistanceM: 5021,
            avgPaceSPerKm: 299,
            isPb: true),
        ResultEntry(
            userId: 7,
            nickname: '지현',
            status: ResultEntryStatus.finished,
            rank: 1,
            recordTimeS: 1502,
            totalDistanceM: 5008,
            avgPaceSPerKm: 300,
            isPb: false),
        ResultEntry(
            userId: 5,
            nickname: '현우',
            status: ResultEntryStatus.finished,
            rank: 3,
            recordTimeS: 1640,
            totalDistanceM: 5033,
            avgPaceSPerKm: 326,
            isPb: false),
        // DNF — rank/record/pace null(하단).
        ResultEntry(
            userId: 8,
            nickname: '다은',
            status: ResultEntryStatus.dnf,
            rank: null,
            recordTimeS: null,
            totalDistanceM: 3120,
            avgPaceSPerKm: null,
            isPb: false),
        // DNS — 전 기록 키부재=null(P46-1 안전 렌더).
        ResultEntry(
            userId: 9,
            nickname: '탈퇴한 러너',
            status: ResultEntryStatus.dns,
            rank: null,
            recordTimeS: null,
            totalDistanceM: null,
            avgPaceSPerKm: null,
            isPb: false),
      ],
    );

// overrides 는 List<Override> — flutter_riverpod 이 Override 타입명을 재노출하지
// 않아 dynamic 으로 받아 암시적 변환한다(테스트 헬퍼 편의).
Widget _wrap(dynamic overrides) => ProviderScope(
      overrides: overrides,
      child: const MaterialApp(home: SessionResultScreen(sessionId: 91)),
    );

void main() {
  testWidgets('결과 확정: 동률 공동순위·PB 뱃지(is_pb만)·DNF/DNS 하단·null 안전', (tester) async {
    await tester.pumpWidget(_wrap([
      trackRepositoryProvider
          .overrideWithValue(FakeTrackRepository(resultOutcome: ResultReady(_result()))),
    ]));
    await tester.pumpAndSettle();

    // 동률 공동순위(1,1,3): rank '1' 이 정확히 2개(공동 1위 — 1,2 아님).
    expect(find.text('1'), findsNWidgets(2));
    expect(find.text('3'), findsWidgets); // 3위 + "3명 완주" 헤더

    // PB 뱃지는 is_pb=true 인 1명만.
    expect(find.text('🏅 개인 최고 기록'), findsOneWidget);

    // DNF/DNS 하단 표기.
    expect(find.text('미완주'), findsOneWidget); // DNF
    expect(find.text('불참'), findsOneWidget); // DNS

    // 키부재=null 안전 렌더: DNF/DNS 기록 "--:--", 페이스 "-".
    expect(find.text('--:--'), findsNWidgets(2));

    // 리플레이 진입 M3 placeholder.
    expect(find.textContaining('리플레이'), findsOneWidget);
  });

  testWidgets(
      '결과 미확정(RESULT_NOT_READY): 오도성 n/m 없음 · 정성 문구 + 마감 시각 + 실시간 STARTED (R-009)',
      (tester) async {
    // 서버 의미론상 **도달 가능한** 대기 상태: 확정 전엔 participation 이 일괄 전이
    // 되지 않아 참가자는 전원 STARTED(또는 미출주 REGISTERED)로 남는다. FINISHED/
    // DNF 는 확정(COMPLETED=결과 Ready)에서만 생겨 대기 구간엔 존재 불가 — 종전
    // FINISHED2+STARTED1 픽스처는 도달 불가능한 상태라 결함을 가렸다(정정).
    final session = SessionDetail(
      id: 91,
      crewId: 12,
      course: const CourseDetail(
        id: 55,
        name: '한강 5K',
        routePolyline: '',
        distanceM: 5000,
        start: LatLng(37.5, 127.0),
        finish: LatLng(37.5, 127.0),
      ),
      status: RaceStatus.finalizing,
      scheduledAt: DateTime.utc(2026, 7, 10, 21),
      uploadDeadline: DateTime.utc(2026, 7, 11, 9),
      participants: const [
        ParticipantView(
            userId: 3, nickname: '민수', status: ParticipationStatus.started),
        ParticipantView(
            userId: 7, nickname: '지현', status: ParticipationStatus.started),
        ParticipantView(
            userId: 5, nickname: '현우', status: ParticipationStatus.started),
      ],
    );

    await tester.pumpWidget(_wrap([
      trackRepositoryProvider.overrideWithValue(
          FakeTrackRepository(resultOutcome: const ResultPending())),
      sessionRepositoryProvider
          .overrideWithValue(FakeSessionRepository(detail_: session)),
    ]));
    await tester.pumpAndSettle();

    expect(find.text('업로드 완료'), findsOneWidget);
    // 정성 문구 — 수치 진행률 없음.
    expect(find.textContaining('다른 참가자들을 기다리는 중'), findsOneWidget);
    expect(find.textContaining('마감 시각에 순위가 확정'), findsOneWidget);
    expect(find.textContaining('업로드 마감'), findsOneWidget);
    // 오도성 카운트가 화면 어디에도 없어야 한다(R-009 회귀 방지 핵심).
    expect(find.textContaining('/3명'), findsNothing);
    expect(find.textContaining('0/'), findsNothing);
    // 실시간 STARTED 신호는 유지(유효 신호 — 3명 뛰는 중).
    expect(find.textContaining('지금 뛰는 중'), findsOneWidget);
  });
}
