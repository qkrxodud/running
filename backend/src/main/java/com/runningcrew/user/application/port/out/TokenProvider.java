package com.runningcrew.user.application.port.out;

/**
 * 자체 JWT 발급·검증 out-port(계약 auth-api.md — HS256, access 30분/refresh 30일, 쌍 회전).
 *
 * <p>클레임에 kakao_id·닉네임 등 개인정보 금지(봉인 원칙). 검증 실패는 예외로 구분해
 * 필터/서비스가 401 code(AUTH_TOKEN_EXPIRED vs UNAUTHORIZED vs AUTH_REFRESH_INVALID)로 매핑한다.
 */
public interface TokenProvider {

    /** access+refresh 쌍 발급. */
    IssuedTokens issue(long userId);

    /**
     * access 토큰 검증 → 내부 user id.
     *
     * @throws TokenExpiredException 만료(→ AUTH_TOKEN_EXPIRED, 클라 갱신 시도)
     * @throws TokenInvalidException 부재·위조·typ 불일치(→ UNAUTHORIZED, 재로그인)
     */
    long verifyAccess(String token);

    /**
     * refresh 토큰 검증 → 내부 user id.
     *
     * @throws TokenRefreshInvalidException 만료·위조·typ!=refresh(→ AUTH_REFRESH_INVALID)
     */
    long verifyRefresh(String token);
}
