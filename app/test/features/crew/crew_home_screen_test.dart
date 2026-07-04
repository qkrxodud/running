import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/model/crew_dtos.dart';
import 'package:running/features/crew/crew_home_screen.dart';

import '../../support/fakes.dart';

void main() {
  testWidgets('내 크루 목록을 카드로 렌더한다', (tester) async {
    final summary = CrewSummary(
      id: 12,
      name: '새벽 한강 크루',
      status: CrewStatus.active,
      memberCount: 6,
      role: CrewRole.leader,
      createdAt: DateTime.utc(2026, 6, 1),
    );
    await tester.pumpWidget(ProviderScope(
      overrides: [
        crewRepositoryProvider
            .overrideWithValue(FakeCrewRepository(crews: [summary])),
      ],
      child: const MaterialApp(home: CrewHomeScreen()),
    ));
    await tester.pumpAndSettle();

    expect(find.text('새벽 한강 크루'), findsOneWidget);
    expect(find.text('멤버 6명'), findsOneWidget);
    expect(find.text('크루장'), findsOneWidget);
    expect(find.text('크루 만들기'), findsOneWidget);
  });

  testWidgets('크루가 없으면 빈 상태를 표시한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository(crews: [])),
      ],
      child: const MaterialApp(home: CrewHomeScreen()),
    ));
    await tester.pumpAndSettle();

    expect(find.text('아직 소속된 크루가 없어요'), findsOneWidget);
  });
}
