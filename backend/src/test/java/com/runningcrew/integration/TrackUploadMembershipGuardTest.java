package com.runningcrew.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.race.domain.LatLng;
import com.runningcrew.race.domain.PolylineCodec;
import com.runningcrew.support.AbstractMySqlIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * W46-2 / R-007 회귀 박제: 업로드·start의 권한 경계를 <b>크루 비멤버=403 / 멤버지만 미등록=409</b>로
 * 분리(track-api v0.1.1 §1 · session-api v0.2.1 §6). 평가 순서 404→403→409 배타 — 비멤버에게 세션
 * 존재·상태 누설 금지(Crew invite-only 규범). 403과 409는 동일 호출자에 동시 부여되지 않음.
 */
@AutoConfigureMockMvc
class TrackUploadMembershipGuardTest extends AbstractMySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 업로드_start_권한경계_비멤버403_멤버미등록409() throws Exception {
        String leader = login("stub:r007-leader", "리더");
        long crewId = createCrew(leader, "R007 크루");
        // 멤버: 크루 ACTIVE 멤버이나 세션 미등록(register 안 함).
        String member = login("stub:r007-member", "멤버");
        joinViaLeaderInvite(leader, crewId, member);
        // 아웃사이더: 크루 비멤버.
        String outsider = login("stub:r007-outsider", "아웃사이더");

        List<LatLng> course = List.of(
                new LatLng(37.5000, 127.0000), new LatLng(37.5000, 127.0009),
                new LatLng(37.5000, 127.0018));
        String coursePolyline = PolylineCodec.encode(course);
        long courseId = json(mvc.perform(post("/api/v1/crews/" + crewId + "/courses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseBody("코스", coursePolyline, course.get(0),
                                course.get(course.size() - 1))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long sessionId = json(mvc.perform(post("/api/v1/crews/" + crewId + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(courseId, "2026-07-10T21:00:00Z", "2026-07-11T09:00:00Z")))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        cmd(leader, sessionId, "open").andExpect(status().isOk());

        String body = uploadBody(coursePolyline);

        // (1) 업로드 — 비멤버 → 403 FORBIDDEN (세션 존재·상태 누설 금지)
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // (2) 업로드 — 멤버지만 미등록 → 409 SESSION_STATE_INVALID (선 register 필요)
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));

        // (3) start — 비멤버 → 403 FORBIDDEN
        cmd(outsider, sessionId, "start")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // (4) start — 멤버지만 미등록 → 409 SESSION_STATE_INVALID
        cmd(member, sessionId, "start")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));

        // (참조) 상태 조회 /me — 비멤버 → 403 (누설 금지, track-api §2)
        mvc.perform(get("/api/v1/sessions/" + sessionId + "/track/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String uploadBody(String polyline) {
        return "{\"client_upload_id\":\"r007-1\",\"started_at\":\"2026-07-10T21:00:05Z\","
                + "\"polyline\":\"" + polyline + "\",\"timestamps\":[1752181205000,1752181208000,"
                + "1752181211000],\"speeds\":[0.0,2.8,3.1],\"accuracies\":[12.0,8.5,9.0]}";
    }

    // --- helpers (RaceSessionHttpFlowTest 동형) ---

    private org.springframework.test.web.servlet.ResultActions cmd(String token, long sessionId,
                                                                   String action) throws Exception {
        return mvc.perform(post("/api/v1/sessions/" + sessionId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String login(String stub, String nickname) throws Exception {
        JsonNode login = json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kakao_access_token\":\"" + stub + "\"}")).andReturn());
        String token = login.get("access_token").asText();
        mvc.perform(put("/api/v1/users/me/nickname").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk());
        return token;
    }

    private long createCrew(String token, String name) throws Exception {
        return json(mvc.perform(post("/api/v1/crews").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
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

    private String courseBody(String name, String polyline, LatLng start, LatLng finish) {
        return "{\"name\":\"" + name + "\",\"route_polyline\":\"" + polyline + "\","
                + "\"start_lat\":" + start.lat() + ",\"start_lng\":" + start.lng() + ","
                + "\"finish_lat\":" + finish.lat() + ",\"finish_lng\":" + finish.lng() + "}";
    }

    private String sessionBody(long courseId, String scheduledAt, String uploadDeadline) {
        return "{\"course_id\":" + courseId + ",\"scheduled_at\":\"" + scheduledAt + "\","
                + "\"upload_deadline\":\"" + uploadDeadline + "\"}";
    }

    private JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
