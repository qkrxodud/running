package com.runningcrew.user.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** PUT /api/v1/users/me/nickname 요청(계약 user-api.md §2). 상세 검증(1~30자·제어문자)은 도메인. */
public record NicknameRequest(@NotNull String nickname) {
}
