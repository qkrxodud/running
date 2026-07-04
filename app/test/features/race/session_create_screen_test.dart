import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/model/race_dtos.dart';
import 'package:running/features/race/session_create_screen.dart';

import '../../support/fakes.dart';

CourseSummary _course(int id, String name) => CourseSummary(
      id: id,
      crewId: 12,
      name: name,
      distanceM: 5000,
      createdAt: DateTime.utc(2026, 7, 4),
    );

void main() {
  testWidgets('시드 코스 목록을 선택지로 렌더한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        courseRepositoryProvider.overrideWithValue(
          FakeCourseRepository(courses: [
            _course(55, '한강 5K'),
            _course(56, '남산 순환'),
          ]),
        ),
        sessionRepositoryProvider.overrideWithValue(FakeSessionRepository()),
      ],
      child: const MaterialApp(home: SessionCreateScreen(crewId: 12)),
    ));
    await tester.pumpAndSettle();

    expect(find.text('한강 5K'), findsOneWidget);
    expect(find.text('남산 순환'), findsOneWidget);
    expect(find.text('세션 만들기'), findsWidgets);
  });

  testWidgets('코스 미선택 상태로 제출하면 검증 오류', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        courseRepositoryProvider.overrideWithValue(
          FakeCourseRepository(courses: [_course(55, '한강 5K')]),
        ),
        sessionRepositoryProvider.overrideWithValue(FakeSessionRepository()),
      ],
      child: const MaterialApp(home: SessionCreateScreen(crewId: 12)),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, '세션 만들기'));
    await tester.pump();

    expect(find.text('코스를 선택해 주세요.'), findsOneWidget);
  });

  testWidgets('코스 없으면 안내 문구', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        courseRepositoryProvider
            .overrideWithValue(FakeCourseRepository(courses: const [])),
        sessionRepositoryProvider.overrideWithValue(FakeSessionRepository()),
      ],
      child: const MaterialApp(home: SessionCreateScreen(crewId: 12)),
    ));
    await tester.pumpAndSettle();

    expect(find.text('선택할 코스가 없어요'), findsOneWidget);
  });
}
