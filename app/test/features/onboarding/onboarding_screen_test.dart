import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/features/onboarding/onboarding_screen.dart';

void main() {
  testWidgets('온보딩 닉네임 화면을 렌더한다', (tester) async {
    await tester.pumpWidget(const ProviderScope(
      child: MaterialApp(home: OnboardingScreen()),
    ));

    expect(find.text('어떻게 불러드릴까요?'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, '시작하기'), findsOneWidget);
  });

  testWidgets('빈 닉네임 제출은 검증 오류를 표시한다', (tester) async {
    await tester.pumpWidget(const ProviderScope(
      child: MaterialApp(home: OnboardingScreen()),
    ));

    await tester.tap(find.widgetWithText(FilledButton, '시작하기'));
    await tester.pump();

    expect(find.text('닉네임을 입력해 주세요.'), findsOneWidget);
  });
}
