import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/features/settings/settings_screen.dart';

void main() {
  testWidgets('설정 화면이 로그아웃·회원 탈퇴 진입점을 표시한다', (tester) async {
    await tester.pumpWidget(const ProviderScope(
      child: MaterialApp(home: SettingsScreen()),
    ));
    await tester.pump();

    expect(find.text('로그아웃'), findsOneWidget);
    expect(find.text('회원 탈퇴'), findsOneWidget);
  });

  testWidgets('탈퇴 시 "재로그인=신규 계정, 복구 불가" 고지를 보여준다', (tester) async {
    await tester.pumpWidget(const ProviderScope(
      child: MaterialApp(home: SettingsScreen()),
    ));
    await tester.pump();

    await tester.tap(find.text('회원 탈퇴'));
    await tester.pumpAndSettle();

    // user-api.md §3 요건: 복구 불가·새 계정 고지.
    expect(find.textContaining('새 계정'), findsOneWidget);
    expect(find.textContaining('복구할 수 없습니다'), findsOneWidget);
  });
}
