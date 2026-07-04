/// 계약 공통 오류 shape `{code, message}` (conventions.md §4) — 순수 모델.
///
/// 클라이언트 분기는 **code 로만** 한다. message 문자열 매칭 금지(로케일 변동).
class ApiErrorBody {
  const ApiErrorBody({required this.code, required this.message});

  final String code;
  final String message;

  factory ApiErrorBody.fromJson(Map<String, dynamic> json) => ApiErrorBody(
        code: json['code'] as String? ?? 'UNKNOWN',
        message: json['message'] as String? ?? '',
      );
}

/// 인증 401 세분 code (auth-api.md §3, conventions v0.1.1).
class AuthErrorCodes {
  const AuthErrorCodes._();

  /// access 만료 → refresh 1회 후 원요청 재시도.
  static const tokenExpired = 'AUTH_TOKEN_EXPIRED';

  /// 토큰 부재·위조·WITHDRAWN → 즉시 토큰 폐기·재로그인.
  static const unauthorized = 'UNAUTHORIZED';

  /// refresh 갱신 불가 → 토큰 폐기·재로그인.
  static const refreshInvalid = 'AUTH_REFRESH_INVALID';

  /// 카카오 토큰 검증 실패 (로그인 엔드포인트) — 사용자 자격 문제, 재시도/재입력.
  static const kakaoTokenInvalid = 'AUTH_KAKAO_TOKEN_INVALID';

  /// 카카오 kapi **서버 장애**(503, auth-api §1 v0.1.1) — 사용자 탓 아님.
  /// **잠시 후 재시도**(재로그인 유도 금지 — 무한 루프 방지). 401과 의미론 분리.
  static const kakaoUnavailable = 'AUTH_KAKAO_UNAVAILABLE';
}

/// API 오류 예외 — 어댑터가 HTTP 오류를 이 형태로 정규화해 상위에 전달한다.
class ApiException implements Exception {
  const ApiException({
    required this.statusCode,
    required this.code,
    required this.message,
  });

  /// HTTP 상태. 네트워크 단절 등 응답 자체가 없으면 0.
  final int statusCode;

  /// 계약 code (UPPER_SNAKE). 응답 파싱 불가 시 'NETWORK_ERROR' 등 클라 자체 코드.
  final String code;

  final String message;

  bool get isNetworkError => statusCode == 0;

  @override
  String toString() => 'ApiException($statusCode, $code, $message)';
}
