package com.runningcrew.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 앱↔서버 계약 3자 대조의 "라이브 곡선"을 테스트로 박제: 스텁 로그인 → JWT → 크루 생성 → 초대 코드 →
 * 참가 왕복이 계약(auth/user/crew-api.md)의 snake_case 필드·상태코드와 문자 단위로 일치하는지 검증한다.
 *
 * <p>{@code @AutoConfigureMockMvc}가 FilterRegistrationBean으로 등록된 JWT 인증 필터까지 포함해 구성한다.
 */
@AutoConfigureMockMvc
class AuthCrewHttpFlowTest extends AbstractMySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private MockMvc mvc() {
        return mvc;
    }

    @Test
    void 로그인_온보딩_크루생성_초대코드_참가_왕복이_계약과_일치한다() throws Exception {
        // 1. 스텁 로그인 → 신규 User
        JsonNode login1 = json(mvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakao_access_token\":\"stub:http-leader\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(1800))
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andExpect(jsonPath("$.user.onboarding_completed").value(false))
                .andReturn());
        String leaderToken = login1.get("access_token").asText();
        long leaderId = login1.get("user").get("id").asLong();

        // 2. 보호 API — 미인증 401 UNAUTHORIZED
        mvc().perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 3. 온보딩 닉네임 설정 → onboarding_completed true
        mvc().perform(put("/api/v1/users/me/nickname").header(HttpHeaders.AUTHORIZATION, bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"민수\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("민수"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.onboarding_completed").value(true))
                .andExpect(jsonPath("$.created_at").exists());

        // 4. 크루 생성 → 201, 생성자가 leader이자 유일 멤버
        JsonNode crew = json(mvc().perform(post("/api/v1/crews").header(HttpHeaders.AUTHORIZATION, bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새벽 러닝크루\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.leader.user_id").value(leaderId))
                .andExpect(jsonPath("$.members[0].role").value("LEADER"))
                .andExpect(jsonPath("$.members[0].joined_at").exists())
                .andReturn());
        long crewId = crew.get("id").asLong();

        // 5. 초대 코드 생성 → 201, 6자 코드(혼동문자 제외)
        JsonNode invite = json(mvc().perform(post("/api/v1/crews/" + crewId + "/invite-codes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leaderToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"max_uses\":5,\"expires_in_hours\":72}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.crew_id").value(crewId))
                .andExpect(jsonPath("$.used_count").value(0))
                .andExpect(jsonPath("$.expires_at").exists())
                .andReturn());
        String code = invite.get("code").asText();
        assertThat(code).matches("[2-9A-HJ-NP-Z]{6}");   // 0/O/1/I 제외 대문자+숫자 6자

        // 6. 두 번째 유저 로그인 → 참가(소문자 코드로 보내 대문자 정규화 검증)
        JsonNode login2 = json(mvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakao_access_token\":\"stub:http-member\"}"))
                .andReturn());
        String memberToken = login2.get("access_token").asText();
        long memberId = login2.get("user").get("id").asLong();

        mvc().perform(post("/api/v1/crews/join").header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(crewId))
                .andExpect(jsonPath("$.members.length()").value(2));

        // 7. 내 크루 목록(멤버) → 페이지 래퍼 + role MEMBER
        mvc().perform(get("/api/v1/crews").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].member_count").value(2))
                .andExpect(jsonPath("$.items[0].role").value("MEMBER"));

        // 8. 재참가 시도 → 409 ALREADY_JOINED
        mvc().perform(post("/api/v1/crews/join").header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));

        // 9. 없는 코드 → 404 INVITE_CODE_INVALID
        mvc().perform(post("/api/v1/crews/join").header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ZZZZZZ\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_INVALID"));

        // 10. 비멤버 크루 상세 조회 → 403 FORBIDDEN
        JsonNode login3 = json(mvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakao_access_token\":\"stub:http-outsider\"}"))
                .andReturn());
        mvc().perform(get("/api/v1/crews/" + crewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login3.get("access_token").asText())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(memberId).isNotEqualTo(leaderId);
    }

    @Test
    void 잘못된_카카오_토큰은_401_AUTH_KAKAO_TOKEN_INVALID() throws Exception {
        mvc().perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakao_access_token\":\"not-a-stub-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_KAKAO_TOKEN_INVALID"));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
