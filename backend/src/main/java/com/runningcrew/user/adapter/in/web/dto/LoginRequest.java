package com.runningcrew.user.adapter.in.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/login 요청(계약 auth-api.md §1). JSON은 snake_case(kakao_access_token).
 *
 * @param kakaoAccessToken 카카오 액세스 토큰(스텁 모드는 {@code stub:{fake_kakao_id}})
 * @param clientMeta 디버깅용 자유 형식(서버는 판정에 사용 금지 — conventions §8)
 */
public record LoginRequest(
        @NotBlank String kakaoAccessToken,
        JsonNode clientMeta) {
}
