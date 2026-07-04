import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/features/crew/crew_create_screen.dart';

import '../../support/fakes.dart';

void main() {
  testWidgets('크루 생성 폼을 렌더한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository()),
      ],
      child: const MaterialApp(home: CrewCreateScreen()),
    ));

    expect(find.text('크루 이름'), findsOneWidget);
    expect(find.byType(TextField), findsOneWidget);
  });

  testWidgets('빈 이름으로 제출하면 검증 오류를 표시한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        crewRepositoryProvider.overrideWithValue(FakeCrewRepository()),
      ],
      child: const MaterialApp(home: CrewCreateScreen()),
    ));

    await tester.tap(find.widgetWithText(FilledButton, '크루 만들기'));
    await tester.pump();

    expect(find.text('크루 이름을 입력해 주세요.'), findsOneWidget);
  });
}
