package com.runningcrew.user.adapter.in.web.dto;

import com.runningcrew.user.application.LoginResult;

/**
 * POST /api/v1/auth/login 응답(계약 auth-api.md §1).
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean isNewUser,
        UserSummary user) {

    public record UserSummary(long id, String nickname, boolean onboardingCompleted) {
    }

    public static LoginResponse from(LoginResult r) {
        return new LoginResponse(
                r.tokens().accessToken(),
                r.tokens().refreshToken(),
                "Bearer",
                r.tokens().accessExpiresInSeconds(),
                r.newUser(),
                new UserSummary(r.user().getId(), r.user().getNickname(),
                        r.user().isOnboardingCompleted()));
    }
}
