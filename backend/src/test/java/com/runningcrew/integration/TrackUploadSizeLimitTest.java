package com.runningcrew.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W46-1 / R-006 회귀 박제: track-api TK-3 본문 바이트 상한(≤8 MiB, 외부화)이 배선되어
 * 상한 초과 시 <b>413 TRACK_TOO_LARGE</b>를 {@code {code,message}} 규약으로 반환하는지 검증.
 *
 * <p>상한은 설정 외부화(`track.max-request-bytes`)라 테스트는 이를 512바이트로 낮춰 경계(상한 직후)를
 * 저비용으로 재현한다. Content-Length 프리체크(인터셉터 preHandle)가 세션/참가 검증 이전에 차단하므로
 * 존재하지 않는 세션 id여도 413이 선행한다(디코딩·본문 버퍼링 전 방어).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "track.max-request-bytes=512")
class TrackUploadSizeLimitTest extends AbstractMySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 본문_바이트_상한_직후_413_TRACK_TOO_LARGE() throws Exception {
        String token = login("stub:size-guard", "사이즈가드");

        // 512바이트 상한을 명백히 넘는 본문(폴리라인 문자열 2000자 패딩 → 본문 ≈ 2.1KB).
        String polyline = "a".repeat(2000);
        String body = "{\"client_upload_id\":\"sz-1\",\"started_at\":\"2026-07-10T21:00:00Z\","
                + "\"polyline\":\"" + polyline + "\",\"timestamps\":[1752181205000],"
                + "\"speeds\":[1.0],\"accuracies\":[1.0]}";

        mvc.perform(post("/api/v1/sessions/999999/track")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("TRACK_TOO_LARGE"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private String login(String stub, String nickname) throws Exception {
        JsonNode login = om.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakao_access_token\":\"" + stub + "\"}"))
                .andReturn().getResponse().getContentAsString());
        return login.get("access_token").asText();
    }
}
