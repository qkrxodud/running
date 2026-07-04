package com.runningcrew.crew.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** POST /api/v1/crews/{crewId}/invite-codes 요청(계약 crew-api.md §4). */
public record InviteCodeCreateRequest(
        @NotNull @Min(1) @Max(100) Integer maxUses,
        @NotNull @Min(1) @Max(720) Integer expiresInHours) {
}
