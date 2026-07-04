package com.runningcrew.user.adapter.in.web.dto;

import com.runningcrew.user.application.port.out.IssuedTokens;

/** POST /api/v1/auth/refresh 응답(계약 auth-api.md §2 — is_new_user·user 제외). */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {

    public static TokenResponse from(IssuedTokens t) {
        return new TokenResponse(t.accessToken(), t.refreshToken(), "Bearer",
                t.accessExpiresInSeconds());
    }
}
