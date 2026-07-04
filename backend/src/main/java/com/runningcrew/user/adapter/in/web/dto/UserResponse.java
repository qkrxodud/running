package com.runningcrew.user.adapter.in.web.dto;

import com.runningcrew.user.domain.User;
import com.runningcrew.user.domain.UserStatus;
import java.time.Instant;

/** GET/PUT /api/v1/users/me 응답(계약 user-api.md §1). */
public record UserResponse(
        long id,
        String nickname,
        UserStatus status,
        boolean onboardingCompleted,
        Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getNickname(), u.getStatus(),
                u.isOnboardingCompleted(), u.getCreatedAt());
    }
}
