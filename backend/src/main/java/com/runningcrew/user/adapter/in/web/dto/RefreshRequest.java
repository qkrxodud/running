package com.runningcrew.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/refresh 요청(계약 auth-api.md §2). */
public record RefreshRequest(@NotBlank String refreshToken) {
}
