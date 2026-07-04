import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/model/crew_dtos.dart';
import 'package:running/features/crew/crew_detail_screen.dart';

import '../../support/fakes.dart';

void main() {
  testWidgets('크루 상세가 이름과 멤버 목록(인앱 갈음)을 렌더한다', (tester) async {
    final detail = CrewDetail(
      id: 12,
      name: '새벽 한강 크루',
      status: CrewStatus.active,
      leader: const CrewMemberView(
          userId: 3, nickname: '지훈', role: CrewRole.leader),
      createdAt: DateTime.utc(2026, 6, 1),
      members: [
        CrewMemberView(
            userId: 3,
            nickname: '지훈',
            role: CrewRole.leader,
            joinedAt: DateTime.utc(2026, 6, 2)),
        CrewMemberView(
            userId: 7,
            nickname: '민재',
            role: CrewRole.member,
            joinedAt: DateTime.utc(2026, 6, 3)),
      ],
    );
    await tester.pumpWidget(ProviderScope(
      overrides: [
        crewRepositoryProvider
            .overrideWithValue(FakeCrewRepository(detail_: detail)),
      ],
      child: const MaterialApp(home: CrewDetailScreen(crewId: 12)),
    ));
    await tester.pumpAndSettle();

    expect(find.text('새벽 한강 크루'), findsOneWidget);
    expect(find.text('지훈'), findsWidgets);
    expect(find.text('민재'), findsOneWidget);
    expect(find.text('크루장'), findsOneWidget);
  });
}
