import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/geo/lat_lng.dart';
import 'package:running/core/model/history_dtos.dart';
import 'package:running/core/model/race_dtos.dart';
import 'package:running/core/model/track_dtos.dart';
import 'package:running/features/history/history_screen.dart';

import '../../support/fakes.dart';

RecordHistoryItem _record({
  required int trackId,
  required FinishStatus status,
  bool isPb = false,
  bool cancelled = false,
  int? rank,
}) =>
    RecordHistoryItem(
      trackRecordId: trackId,
      sessionId: 91,
      courseId: 55,
      courseName: '한강 5K',
      scheduledAt: DateTime.utc(2026, 7, 10, 21),
      finishStatus: status,
      rank: rank,
      recordTimeS: status == FinishStatus.finished ? 1502 : null,
      totalDistanceM: 5021,
      avgPaceSPerKm: status == FinishStatus.finished ? 299 : null,
      isPb: isPb,
      sessionCancelled: cancelled,
    );

// overrides 는 List<Override> — Override 타입명 미노출로 dynamic 수신(암시적 변환).
Widget _wrap(dynamic overrides) => ProviderScope(
      overrides: overrides,
      child: const MaterialApp(home: HistoryScreen()),
    );

void main() {
  testWidgets('기록 탭: 완주(PB 뱃지)·DNF(미완주)·취소된 세션 배지 렌더', (tester) async {
    await tester.pumpWidget(_wrap([
      historyRepositoryProvider.overrideWithValue(FakeHistoryRepository(records: [
        _record(trackId: 1, status: FinishStatus.finished, isPb: true, rank: 1),
        _record(trackId: 2, status: FinishStatus.dnf),
        _record(trackId: 3, status: FinishStatus.finished, cancelled: true),
      ])),
    ]));
    await tester.pumpAndSettle();

    expect(find.text('🏅 PB'), findsOneWidget); // is_pb 완주만
    expect(find.text('미완주'), findsOneWidget); // DNF 표기
    expect(find.text('취소된 세션'), findsOneWidget); // CANCELLED 배지
  });

  testWidgets('승격 버튼: FINISHED 항목에만 "코스로 만들기" 노출', (tester) async {
    await tester.pumpWidget(_wrap([
      historyRepositoryProvider.overrideWithValue(FakeHistoryRepository(records: [
        _record(trackId: 1, status: FinishStatus.finished, rank: 1), // 노출
        _record(trackId: 2, status: FinishStatus.dnf), // 미노출
      ])),
    ]));
    await tester.pumpAndSettle();

    // 완주 1건만 승격 버튼(취소 세션 완주도 가능하나 여기선 완주 1건).
    expect(find.text('코스로 만들기'), findsOneWidget);
  });

  testWidgets('승격 성공 → 코스 만들기 다이얼로그 → promote 호출·크루 코스 반영', (tester) async {
    int? promotedTrackId;
    int? promotedCrewId;
    await tester.pumpWidget(_wrap([
      historyRepositoryProvider.overrideWithValue(FakeHistoryRepository(records: [
        _record(trackId: 42, status: FinishStatus.finished, rank: 1),
      ])),
      // 승격은 crewId 해석을 위해 세션 상세 조회 → FakeSessionRepository(crewId=12).
      sessionRepositoryProvider.overrideWithValue(FakeSessionRepository()),
      courseRepositoryProvider.overrideWithValue(FakeCourseRepository(
        onPromote: (crewId, trackId, name) {
          promotedCrewId = crewId;
          promotedTrackId = trackId;
          return _promotedCourse(name);
        },
      )),
    ]));
    await tester.pumpAndSettle();

    await tester.tap(find.text('코스로 만들기'));
    await tester.pumpAndSettle();
    // 다이얼로그 → "만들기" 확정.
    expect(find.text('코스로 만들기'), findsWidgets); // 다이얼로그 타이틀
    await tester.tap(find.text('만들기'));
    await tester.pumpAndSettle();

    expect(promotedTrackId, 42, reason: 'source_track_record_id 전달');
    expect(promotedCrewId, 12, reason: '세션 상세로 crewId 해석');
  });

  testWidgets('개인 최고 탭: PB 카드 렌더', (tester) async {
    await tester.pumpWidget(_wrap([
      historyRepositoryProvider.overrideWithValue(FakeHistoryRepository(pbs: [
        PersonalBestItem(
          courseId: 55,
          courseName: '한강 5K',
          distanceM: 5000,
          bestRecordTimeS: 1502,
          avgPaceSPerKm: 299,
          achievedSessionId: 91,
          achievedAt: DateTime.utc(2026, 7, 10, 21),
        ),
      ])),
    ]));
    await tester.pumpAndSettle();

    await tester.tap(find.text('개인 최고'));
    await tester.pumpAndSettle();

    expect(find.text('한강 5K'), findsOneWidget);
    expect(find.text('25:02'), findsOneWidget); // 1502s = 25:02
  });

  testWidgets('빈 기록: 안내 문구', (tester) async {
    await tester.pumpWidget(_wrap([
      historyRepositoryProvider
          .overrideWithValue(FakeHistoryRepository(records: const [])),
    ]));
    await tester.pumpAndSettle();
    expect(find.textContaining('완주한 기록이 없어요'), findsOneWidget);
  });
}

CourseDetail _promotedCourse(String name) => CourseDetail(
      id: 200,
      crewId: 12,
      name: name,
      routePolyline: '_p~iF~ps|U',
      distanceM: 5043,
      start: const LatLng(37.5, 127.0),
      finish: const LatLng(37.52, 127.02),
    );
