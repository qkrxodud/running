package com.runningcrew.crew.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/v1/crews 요청(계약 crew-api.md §1). 상세 검증(trim 1~50자)은 도메인. */
public record CreateCrewRequest(@NotNull String name) {
}
