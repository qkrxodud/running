import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/model/api_error.dart';
import 'package:running/features/auth/login_screen.dart';

import '../../support/fakes.dart';

/// 503 AUTH_KAKAO_UNAVAILABLE — "잠시 후 재시도" 안내(재로그인 루프 금지).
/// 401(자격 문제)과 의미론 분리 검증.
void main() {
  Widget wrap(Object thrown) => ProviderScope(
        overrides: [
          kakaoLoginReadyProvider.overrideWithValue(true),
          kakaoAuthServiceProvider
              .overrideWithValue(const FakeKakaoAuthService()),
          authRepositoryProvider
              .overrideWithValue(FakeAuthRepository(throwOnLogin: thrown)),
        ],
        child: const MaterialApp(home: LoginScreen()),
      );

  testWidgets('503 kapi 장애 → 재시도 안내(재로그인 유도 아님)', (tester) async {
    await tester.pumpWidget(wrap(const ApiException(
      statusCode: 503,
      code: AuthErrorCodes.kakaoUnavailable,
      message: '카카오 일시 장애',
    )));
    await tester.pumpAndSettle();

    await tester.tap(find.text('카카오로 시작하기'));
    await tester.pumpAndSettle();

    expect(find.textContaining('잠시 후 다시 시도'), findsOneWidget);
    // 화면은 로그인 화면에 그대로 — 재로그인 루프/강제 이동 없음.
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('401 kakaoTokenInvalid → 인증 실패 안내(503과 다른 문구)', (tester) async {
    await tester.pumpWidget(wrap(const ApiException(
      statusCode: 401,
      code: AuthErrorCodes.kakaoTokenInvalid,
      message: '검증 거부',
    )));
    await tester.pumpAndSettle();

    await tester.tap(find.text('카카오로 시작하기'));
    await tester.pumpAndSettle();

    expect(find.textContaining('카카오 인증에 실패'), findsOneWidget);
    expect(find.textContaining('잠시 후 다시 시도'), findsNothing);
  });
}
