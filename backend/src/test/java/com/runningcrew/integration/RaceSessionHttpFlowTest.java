package com.runningcrew.integration;

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
 * Race 컨텍스트 "라이브 곡선" 박제: 로그인 → 크루 → 코스 생성 → 세션 생성→open→register→start→cancel
 * 왕복이 계약(course-api.md·session-api.md)의 snake_case 필드·상태코드·상태머신과 문자 단위로 일치하는지.
 */
@AutoConfigureMockMvc
class RaceSessionHttpFlowTest extends AbstractMySqlIntegrationTest {

    private static final String GOLDEN_POLYLINE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 코스생성_세션_open_register_start_cancel_왕복이_계약과_일치한다() throws Exception {
        String leader = loginNickname("stub:race-leader", "리더");
        long leaderId = userId(leader);
        long crewId = createCrew(leader, "레이스 크루");

        // 1. 비크루장(멤버) 코스 생성 시도 → 403
        String member = loginNickname("stub:race-member", "멤버");
        joinViaLeaderInvite(leader, crewId, member);
        mvc.perform(post("/api/v1/crews/" + crewId + "/courses").header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(courseBody("멤버코스")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. 크루장 코스 생성 → 201, distance_m 서버 확정, route_polyline 에코, created_by=리더
        JsonNode course = json(mvc.perform(post("/api/v1/crews/" + crewId + "/courses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON).content(courseBody("한강 5K")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("한강 5K"))
                .andExpect(jsonPath("$.route_polyline").value(GOLDEN_POLYLINE))
                .andExpect(jsonPath("$.crew_id").value(crewId))
                .andExpect(jsonPath("$.created_by").value(leaderId))
                .andReturn());
        long courseId = course.get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(course.get("distance_m").asInt()).isPositive();

        // 3. 잘못된 폴리라인 → 400 VALIDATION_ERROR
        mvc.perform(post("/api/v1/crews/" + crewId + "/courses").header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"깨진코스\",\"route_polyline\":\"_p~iF\",\"start_lat\":37.5,"
                                + "\"start_lng\":127.0,\"finish_lat\":37.5,\"finish_lng\":127.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 4. 코스 목록(멤버 조회 가능) → 폴리라인 미포함 요약
        mvc.perform(get("/api/v1/crews/" + crewId + "/courses").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].name").value("한강 5K"))
                .andExpect(jsonPath("$.items[0].distance_m").isNumber());

        // 5. 코스 상세 → 폴리라인 포함
        mvc.perform(get("/api/v1/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route_polyline").value(GOLDEN_POLYLINE));

        // 6. upload_deadline <= scheduled_at → 400
        mvc.perform(post("/api/v1/crews/" + crewId + "/sessions").header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(courseId, "2026-07-10T21:00:00Z", "2026-07-10T20:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 7. 세션 생성 → 201, status DRAFT
        JsonNode session = json(mvc.perform(post("/api/v1/crews/" + crewId + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(courseId, "2026-07-10T21:00:00Z", "2026-07-11T09:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.course.id").value(courseId))
                .andExpect(jsonPath("$.course.route_polyline").value(GOLDEN_POLYLINE))
                .andReturn());
        long sessionId = session.get("id").asLong();

        // 8. DRAFT에서 register → 409 SESSION_STATE_INVALID (매트릭스)
        cmd(member, sessionId, "register").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));

        // 9. open (크루장) → OPEN
        cmd(leader, sessionId, "open").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        // 재 open → 409
        cmd(leader, sessionId, "open").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));

        // 10. 선 register 없이 start → 409 (participation 부재)
        cmd(member, sessionId, "start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));

        // 11. register (멤버, OPEN) → 200, 참가자 REGISTERED. 멱등 재호출도 200.
        cmd(member, sessionId, "register").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.participants[0].status").value("REGISTERED"));
        cmd(member, sessionId, "register").andExpect(status().isOk());   // 멱등

        // 12. start (멤버) → 최초 STARTED가 OPEN→RUNNING
        cmd(member, sessionId, "start").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.participants[0].status").value("STARTED"));
        // 멱등 start → RUNNING 유지
        cmd(member, sessionId, "start").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // 13. cancel (크루장, RUNNING 중) → CANCELLED
        cmd(leader, sessionId, "cancel").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // 종료 상태 재전이 → 409
        cmd(leader, sessionId, "open").andExpect(status().isConflict());
        cmd(leader, sessionId, "cancel").andExpect(status().isConflict());

        // 14. 비멤버 세션 상세 → 403
        String outsider = loginNickname("stub:race-outsider", "아웃사이더");
        mvc.perform(get("/api/v1/sessions/" + sessionId).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.ResultActions cmd(String token, long sessionId, String action)
            throws Exception {
        return mvc.perform(post("/api/v1/sessions/" + sessionId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String loginNickname(String stub, String nickname) throws Exception {
        JsonNode login = json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kakao_access_token\":\"" + stub + "\"}")).andReturn());
        String token = login.get("access_token").asText();
        mvc.perform(put("/api/v1/users/me/nickname").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk());
        return token;
    }

    private long userId(String token) throws Exception {
        JsonNode me = json(mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn());
        return me.get("id").asLong();
    }

    private long createCrew(String token, String name) throws Exception {
        JsonNode crew = json(mvc.perform(post("/api/v1/crews").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        return crew.get("id").asLong();
    }

    private void joinViaLeaderInvite(String leaderToken, long crewId, String memberToken) throws Exception {
        JsonNode invite = json(mvc.perform(post("/api/v1/crews/" + crewId + "/invite-codes")
                .header(HttpHeaders.AUTHORIZATION, bearer(leaderToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"max_uses\":5,\"expires_in_hours\":72}"))
                .andReturn());
        String code = invite.get("code").asText();
        mvc.perform(post("/api/v1/crews/join").header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
    }

    private String courseBody(String name) {
        return "{\"name\":\"" + name + "\",\"route_polyline\":\"" + GOLDEN_POLYLINE + "\","
                + "\"start_lat\":38.5,\"start_lng\":-120.2,\"finish_lat\":43.252,\"finish_lng\":-126.453}";
    }

    private String sessionBody(long courseId, String scheduledAt, String uploadDeadline) {
        return "{\"course_id\":" + courseId + ",\"scheduled_at\":\"" + scheduledAt + "\","
                + "\"upload_deadline\":\"" + uploadDeadline + "\"}";
    }

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
