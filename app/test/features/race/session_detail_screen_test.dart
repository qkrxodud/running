import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/geo/lat_lng.dart';
import 'package:running/core/model/race_dtos.dart';
import 'package:running/features/race/session_detail_screen.dart';

import '../../support/fakes.dart';

SessionDetail _session({
  required RaceStatus status,
  List<ParticipantView> participants = const [],
}) =>
    SessionDetail(
      id: 91,
      crewId: 12,
      course: const CourseDetail(
        id: 55,
        name: '한강 5K',
        routePolyline: '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
        distanceM: 5000,
        start: LatLng(37.5, 127.0),
        finish: LatLng(37.52, 127.02),
      ),
      status: status,
      scheduledAt: DateTime.utc(2026, 7, 10, 21),
      uploadDeadline: DateTime.utc(2026, 7, 11, 9),
      participants: participants,
    );

Widget _wrap(SessionDetail detail, int myUserId) => ProviderScope(
      overrides: [
        sessionRepositoryProvider
            .overrideWithValue(FakeSessionRepository(detail_: detail)),
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository()),
        authControllerProvider.overrideWith(() => stubAuthController(myUserId)),
      ],
      child: const MaterialApp(home: SessionDetailScreen(sessionId: 91)),
    );

void main() {
  testWidgets('STARTED 참가자가 있으면 "지금 뛰는 중" 표시', (tester) async {
    await tester.pumpWidget(_wrap(
      _session(
        status: RaceStatus.running,
        participants: const [
          ParticipantView(
              userId: 7, nickname: '지현', status: ParticipationStatus.started),
        ],
      ),
      999,
    ));
    await tester.pumpAndSettle();

    expect(find.text('한강 5K'), findsOneWidget);
    expect(find.textContaining('지금 뛰는 중'), findsWidgets);
    expect(find.text('지현'), findsOneWidget);
  });

  testWidgets('OPEN·미참가 멤버에게 참가 신청 버튼 노출', (tester) async {
    await tester.pumpWidget(_wrap(
      _session(status: RaceStatus.open),
      999, // 참가자 명단에 없음
    ));
    await tester.pumpAndSettle();

    expect(find.text('참가 신청'), findsOneWidget);
    // 미참가자에겐 발행/취소(크루장 전용) 미노출.
    expect(find.text('세션 발행 (참가 개방)'), findsNothing);
  });

  testWidgets('크루장·DRAFT 세션에 발행·취소 버튼 노출', (tester) async {
    await tester.pumpWidget(_wrap(
      _session(status: RaceStatus.draft),
      3, // 리더 userId=3
    ));
    await tester.pumpAndSettle();

    expect(find.text('세션 발행 (참가 개방)'), findsOneWidget);
    expect(find.text('세션 취소'), findsOneWidget);
  });

  testWidgets('참가자에게 레이스 시작(트래킹 M2 안내) 진입점 노출', (tester) async {
    await tester.pumpWidget(_wrap(
      _session(
        status: RaceStatus.open,
        participants: const [
          ParticipantView(
              userId: 999,
              nickname: '나',
              status: ParticipationStatus.registered),
        ],
      ),
      999,
    ));
    await tester.pumpAndSettle();

    expect(find.text('레이스 시작'), findsOneWidget);
    expect(find.textContaining('실제 GPS 트래킹은 다음'), findsOneWidget);
  });
}
