import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/model/api_error.dart';
import 'package:running/core/model/auth_dtos.dart';
import 'package:running/data/auth_repository.dart';
import 'package:running/features/auth/login_screen.dart';
import 'package:running/platform/auth/kakao_auth_service.dart';

import '../../support/fakes.dart';

/// 카카오 로그인 버튼 게이트 + SDK 토큰 → 서버 login 전달 회귀 방지.
///
/// AppConfig.kakaoLoginReady 는 컴파일타임 상수라 위젯에서 직접 검증 불가 →
/// kakaoLoginReadyProvider 를 override 해 활성/비활성 경로를 각각 박제한다.
/// 핵심 계약: 카카오 access token 은 **접두어 없이 그대로** POST /auth/login 의
/// kakao_access_token 으로 전달된다(스텁의 `stub:` 프리픽스 없음 — auth-api §1).

/// 로그인에 넘어온 kakao_access_token 을 기록하는 페이크.
class _RecordingAuthRepository implements AuthRepository {
  String? received;

  @override
  Future<LoginResponse> login(String kakaoAccessToken) async {
    received = kakaoAccessToken;
    return LoginResponse(
      tokens: const TokenPair(accessToken: 'a', refreshToken: 'r'),
      tokenType: 'Bearer',
      expiresIn: 1800,
      isNewUser: true,
      user: const AuthUser(id: 3, nickname: '러너', onboardingCompleted: true),
    );
  }

  @override
  Future<TokenPair?> refresh(String refreshToken) async => null;

  @override
  Future<void> logout() async {}
}

/// 결과(토큰/취소/예외)를 주입하는 카카오 서비스 페이크.
class _FakeKakaoAuthService implements KakaoAuthService {
  _FakeKakaoAuthService({this.token, this.error});

  final String? token;
  final Object? error;

  @override
  Future<String?> login() async {
    if (error != null) throw error!;
    return token; // null = 취소.
  }
}

// riverpod 의 Override 타입은 공개 export 가 아니라 이름으로 못 쓴다 →
// Object 리스트를 받아 ProviderScope 파라미터 타입으로 cast(추론)한다.
Widget _harness(List<Object> overrides) => ProviderScope(
      overrides: overrides.cast(),
      child: const MaterialApp(home: LoginScreen()),
    );

void main() {
  testWidgets('키 미주입(ready=false): 카카오 버튼은 "준비 중" 비활성', (tester) async {
    await tester.pumpWidget(_harness([
      kakaoLoginReadyProvider.overrideWithValue(false),
    ]));

    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, '카카오 로그인 (준비 중 — 키 발급 대기)'),
    );
    expect(button.onPressed, isNull, reason: '준비 전에는 비활성이어야 함');
  });

  testWidgets('ready=true: 성공 토큰이 접두어 없이 서버 login 으로 전달된다',
      (tester) async {
    final auth = _RecordingAuthRepository();
    await tester.pumpWidget(_harness([
      kakaoLoginReadyProvider.overrideWithValue(true),
      kakaoAuthServiceProvider
          .overrideWithValue(_FakeKakaoAuthService(token: 'kakao-real-token')),
      authRepositoryProvider.overrideWithValue(auth),
      userRepositoryProvider.overrideWithValue(FakeUserRepository()),
    ]));

    await tester.tap(find.widgetWithText(FilledButton, '카카오로 시작하기'));
    await tester.pumpAndSettle();

    expect(auth.received, 'kakao-real-token',
        reason: 'stub: 프리픽스 없이 원본 토큰 그대로여야 함(auth-api §1)');
  });

  testWidgets('취소(null): 오류 표시 없이 조용히 종결', (tester) async {
    await tester.pumpWidget(_harness([
      kakaoLoginReadyProvider.overrideWithValue(true),
      kakaoAuthServiceProvider
          .overrideWithValue(_FakeKakaoAuthService(token: null)),
      authRepositoryProvider.overrideWithValue(_RecordingAuthRepository()),
      userRepositoryProvider.overrideWithValue(FakeUserRepository()),
    ]));

    await tester.tap(find.widgetWithText(FilledButton, '카카오로 시작하기'));
    await tester.pumpAndSettle();

    expect(find.textContaining('문제가 발생'), findsNothing);
    expect(find.textContaining('실패'), findsNothing);
  });

  testWidgets('카카오 토큰 거부(401): 오류 문구 표시', (tester) async {
    final err = ApiException(
      code: AuthErrorCodes.kakaoTokenInvalid,
      message: 'invalid',
      statusCode: 401,
    );
    await tester.pumpWidget(_harness([
      kakaoLoginReadyProvider.overrideWithValue(true),
      kakaoAuthServiceProvider.overrideWithValue(
        _FakeKakaoAuthService(token: 'kakao-real-token'),
      ),
      authRepositoryProvider.overrideWithValue(_ThrowingAuthRepository(err)),
      userRepositoryProvider.overrideWithValue(FakeUserRepository()),
    ]));

    await tester.tap(find.widgetWithText(FilledButton, '카카오로 시작하기'));
    await tester.pumpAndSettle();

    expect(find.textContaining('카카오 인증에 실패'), findsOneWidget);
  });

  testWidgets('카카오 SDK 예외(취소 아님): 일반 오류 문구 표시', (tester) async {
    await tester.pumpWidget(_harness([
      kakaoLoginReadyProvider.overrideWithValue(true),
      kakaoAuthServiceProvider.overrideWithValue(
        _FakeKakaoAuthService(error: StateError('sdk boom')),
      ),
      authRepositoryProvider.overrideWithValue(_RecordingAuthRepository()),
      userRepositoryProvider.overrideWithValue(FakeUserRepository()),
    ]));

    await tester.tap(find.widgetWithText(FilledButton, '카카오로 시작하기'));
    await tester.pumpAndSettle();

    expect(find.textContaining('문제가 발생'), findsOneWidget);
  });
}

class _ThrowingAuthRepository implements AuthRepository {
  _ThrowingAuthRepository(this.error);
  final Object error;

  @override
  Future<LoginResponse> login(String kakaoAccessToken) async => throw error;

  @override
  Future<TokenPair?> refresh(String refreshToken) async => null;

  @override
  Future<void> logout() async {}
}
