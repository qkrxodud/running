package com.runningcrew.user.application.port.out;

/**
 * 발급된 토큰 쌍 + access 잔여 수명(초). 계약 응답의 access_token/refresh_token/expires_in에 대응.
 */
public record IssuedTokens(String accessToken, String refreshToken, long accessExpiresInSeconds) {
}
