/// auth-api.md v0.1 계약 DTO.
library;

/// access/refresh 쌍. 갱신은 쌍 회전(둘 다 재발급) — 수신 즉시 구 쌍 덮어쓰기.
class TokenPair {
  const TokenPair({required this.accessToken, required this.refreshToken});

  final String accessToken;
  final String refreshToken;
}

/// 로그인 응답의 user 요약 (auth-api.md §1).
class AuthUser {
  const AuthUser({
    required this.id,
    required this.nickname,
    required this.onboardingCompleted,
  });

  final int id;
  final String nickname;
  final bool onboardingCompleted;

  factory AuthUser.fromJson(Map<String, dynamic> json) => AuthUser(
        id: json['id'] as int,
        nickname: json['nickname'] as String,
        onboardingCompleted: json['onboarding_completed'] as bool? ?? false,
      );
}

/// POST /auth/login 응답 200 (auth-api.md §1).
class LoginResponse {
  const LoginResponse({
    required this.tokens,
    required this.tokenType,
    required this.expiresIn,
    required this.isNewUser,
    required this.user,
  });

  final TokenPair tokens;
  final String tokenType; // 고정 "Bearer"
  final int expiresIn; // access 잔여 수명(초)
  final bool isNewUser;
  final AuthUser user;

  factory LoginResponse.fromJson(Map<String, dynamic> json) => LoginResponse(
        tokens: TokenPair(
          accessToken: json['access_token'] as String,
          refreshToken: json['refresh_token'] as String,
        ),
        tokenType: json['token_type'] as String? ?? 'Bearer',
        expiresIn: json['expires_in'] as int,
        isNewUser: json['is_new_user'] as bool? ?? false,
        user: AuthUser.fromJson(json['user'] as Map<String, dynamic>),
      );
}

/// POST /auth/refresh 응답 200 (auth-api.md §2) — login 에서 user 제외 shape.
class RefreshResponse {
  const RefreshResponse({
    required this.tokens,
    required this.tokenType,
    required this.expiresIn,
  });

  final TokenPair tokens;
  final String tokenType;
  final int expiresIn;

  factory RefreshResponse.fromJson(Map<String, dynamic> json) =>
      RefreshResponse(
        tokens: TokenPair(
          accessToken: json['access_token'] as String,
          refreshToken: json['refresh_token'] as String,
        ),
        tokenType: json['token_type'] as String? ?? 'Bearer',
        expiresIn: json['expires_in'] as int,
      );
}
