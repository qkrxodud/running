import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/model/race_dtos.dart';
import 'package:running/features/race/session_list_screen.dart';

import '../../support/fakes.dart';

SessionSummary _summary(RaceStatus status) => SessionSummary(
      id: 91,
      crewId: 12,
      courseId: 55,
      courseName: '한강 5K',
      status: status,
      scheduledAt: DateTime.utc(2026, 7, 10, 21),
      uploadDeadline: DateTime.utc(2026, 7, 11, 9),
      participantCount: 4,
    );

void main() {
  testWidgets('세션 목록이 코스명·상태 뱃지를 렌더한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        sessionRepositoryProvider.overrideWithValue(
          FakeSessionRepository(sessions: [_summary(RaceStatus.open)]),
        ),
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository()),
        authControllerProvider.overrideWith(() => stubAuthController(3)), // 크루장(리더 userId=3)
      ],
      child: const MaterialApp(home: SessionListScreen(crewId: 12)),
    ));
    await tester.pumpAndSettle();

    expect(find.text('한강 5K'), findsOneWidget);
    expect(find.text('모집 중'), findsOneWidget); // OPEN 라벨
    // 크루장 → 세션 만들기 FAB 노출.
    expect(find.text('세션 만들기'), findsOneWidget);
  });

  testWidgets('크루장이 아니면 세션 만들기 FAB 미노출', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        sessionRepositoryProvider.overrideWithValue(
          FakeSessionRepository(sessions: [_summary(RaceStatus.running)]),
        ),
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository()),
        authControllerProvider.overrideWith(() => stubAuthController(999)), // 리더 아님
      ],
      child: const MaterialApp(home: SessionListScreen(crewId: 12)),
    ));
    await tester.pumpAndSettle();

    expect(find.text('진행 중'), findsOneWidget); // RUNNING 라벨
    expect(find.text('세션 만들기'), findsNothing);
  });

  testWidgets('세션이 없으면 빈 상태 안내', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        sessionRepositoryProvider
            .overrideWithValue(FakeSessionRepository(sessions: const [])),
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository()),
        authControllerProvider.overrideWith(() => stubAuthController(999)),
      ],
      child: const MaterialApp(home: SessionListScreen(crewId: 12)),
    ));
    await tester.pumpAndSettle();

    expect(find.text('아직 예정된 레이스가 없어요'), findsOneWidget);
  });
}
