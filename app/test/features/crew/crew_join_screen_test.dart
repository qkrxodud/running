import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/features/crew/crew_join_screen.dart';

import '../../support/fakes.dart';

void main() {
  testWidgets('초대 코드 입력 폼을 렌더한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository()),
      ],
      child: const MaterialApp(home: CrewJoinScreen()),
    ));

    expect(find.text('크루 참가'), findsOneWidget);
    expect(find.byType(TextField), findsOneWidget);
  });

  testWidgets('형식이 틀린 코드는 검증 오류를 표시한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository()),
      ],
      child: const MaterialApp(home: CrewJoinScreen()),
    ));

    // 4자만 입력 → 6자 미만.
    await tester.enterText(find.byType(TextField), 'ABCD');
    await tester.tap(find.widgetWithText(FilledButton, '크루 참가'));
    await tester.pump();

    expect(find.text('초대 코드는 영문 대문자·숫자 6자입니다.'), findsOneWidget);
  });
}
