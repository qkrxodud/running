package com.runningcrew.crew.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/crews/join 요청(계약 crew-api.md §5). 서버가 대문자 정규화. */
public record JoinCrewRequest(@NotBlank String code) {
}
