package com.runningcrew.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.notification.application.port.out.NotificationSender;
import com.runningcrew.notification.domain.NotificationMessage;
import com.runningcrew.race.domain.LatLng;
import com.runningcrew.race.domain.PolylineCodec;
import com.runningcrew.support.AbstractMySqlIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M3-A/M3-C 라이브 곡선 박제: 2명 확정 → 스냅샷 자동 READY(AFTER_COMMIT @Async) → 조회(표시명 조인·payload
 * 스키마 v1) → 재생성 멱등(admin) → 알림 1회(재생성 후 미재발, RP-12). 비멤버 403.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "admin.token=test-admin-token")
class ReplaySnapshotHttpFlowTest extends AbstractMySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockitoSpyBean NotificationSender notificationSender;

    @Test
    void 확정_자동생성_조회_재생성멱등_알림1회() throws Exception {
        String leader = login("stub:rp-leader", "리더");
        long leaderId = userId(leader);
        long crewId = createCrew(leader, "리플레이 크루");
        String member = login("stub:rp-member", "멤버");
        long memberId = userId(member);
        joinViaLeaderInvite(leader, crewId, member);

        List<LatLng> course = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            course.add(new LatLng(37.5000, 127.0000 + i * 0.0009));
        }
        LatLng finish = course.get(course.size() - 1);
        long courseId = createCourse(leader, crewId, course, finish);
        long sessionId = createSession(leader, crewId, courseId);
        cmd(leader, sessionId, "open").andExpect(status().isOk());
        cmd(leader, sessionId, "register").andExpect(status().isOk());
        cmd(member, sessionId, "register").andExpect(status().isOk());
        cmd(leader, sessionId, "start").andExpect(status().isOk());
        cmd(member, sessionId, "start").andExpect(status().isOk());

        // 확정 전 리플레이 조회 → 404(스냅샷 미생성)
        mvc.perform(get("/api/v1/sessions/" + sessionId + "/replay")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isNotFound());

        long base = System.currentTimeMillis() - 3_600_000L;
        upload(leader, sessionId, "rp-leader", base, withPad(course, finish))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.finish_status").value("FINISHED"));
        // 멤버 DNF(지름길) — 전원 업로드 → 자동 확정 → ResultFinalized → 스냅샷 생성
        upload(member, sessionId, "rp-member", base, new ArrayList<>(course.subList(0, 11)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.finish_status").value("DNF"));

        // 비동기 생성 완료까지 폴링 → READY
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300)).untilAsserted(() ->
                mvc.perform(get("/api/v1/sessions/" + sessionId + "/replay")
                                .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("READY")));

        // READY 응답 상세 검증: 표시명 조인 + payload 스키마 v1
        JsonNode res = json(mvc.perform(get("/api/v1/sessions/" + sessionId + "/replay")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))).andReturn());
        assertThat(res.get("status").asText()).isEqualTo("READY");
        assertThat(res.get("schema_version").asInt()).isEqualTo(1);
        JsonNode names = res.get("display_names");
        assertThat(names.get(String.valueOf(leaderId)).asText()).isEqualTo("리더");
        assertThat(names.get(String.valueOf(memberId)).asText()).isEqualTo("멤버");

        JsonNode payload = res.get("payload");
        assertThat(payload.get("schema_version").asInt()).isEqualTo(1);
        assertThat(payload.get("session_id").asLong()).isEqualTo(sessionId);
        assertThat(payload.get("course").get("distance_m").asInt()).isGreaterThan(0);
        assertThat(payload.get("duration_ms").asLong()).isGreaterThan(0);
        assertThat(payload.has("overtakes")).isTrue();
        JsonNode participants = payload.get("participants");
        assertThat(participants).hasSize(2);
        JsonNode frame0 = participants.get(0).get("frames").get(0);
        assertThat(frame0.get("t_ms").asLong()).isZero();               // t=0
        assertThat(frame0.has("is_gap")).isTrue();
        assertThat(frame0.has("cum_dist_m")).isTrue();
        // DNF 참가자: finish_time_ms 부재(non_null 생략 = null, RP-6)
        JsonNode dnf = participants.get(0).get("user_id").asLong() == memberId
                ? participants.get(0) : participants.get(1);
        assertThat(dnf.get("finish_status").asText()).isEqualTo("DNF");
        assertThat(dnf.has("finish_time_ms")).isFalse();
        // payload에 표시명(nickname) 미내장(RP-3)
        assertThat(participants.get(0).has("nickname")).isFalse();

        // 알림: 최초 READY에 1회 발송됨
        verify(notificationSender, times(1)).send(any(NotificationMessage.class));

        // 재생성(admin) 멱등 — X-Admin-Token → 다시 READY, 알림 미재발(RP-12)
        mvc.perform(post("/api/v1/admin/sessions/" + sessionId + "/replay/regenerate")
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300)).untilAsserted(() ->
                mvc.perform(get("/api/v1/sessions/" + sessionId + "/replay")
                                .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                        .andExpect(jsonPath("$.status").value("READY")));
        verify(notificationSender, times(1)).send(any(NotificationMessage.class));   // 여전히 1회(재발송 금지)

        // admin 토큰 없이 재생성 → 403
        mvc.perform(post("/api/v1/admin/sessions/" + sessionId + "/replay/regenerate"))
                .andExpect(status().isForbidden());

        // 비멤버 조회 → 403
        String outsider = login("stub:rp-outsider", "아웃사이더");
        mvc.perform(get("/api/v1/sessions/" + sessionId + "/replay")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private static List<LatLng> withPad(List<LatLng> course, LatLng finish) {
        List<LatLng> t = new ArrayList<>(course);
        for (int i = 0; i < 3; i++) {
            t.add(finish);
        }
        return t;
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, long sessionId,
            String uploadId, long base, List<LatLng> points) throws Exception {
        int n = points.size();
        StringBuilder ts = new StringBuilder("[");
        StringBuilder sp = new StringBuilder("[");
        StringBuilder ac = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                ts.append(',');
                sp.append(',');
                ac.append(',');
            }
            ts.append(base + i * 30_000L);
            sp.append("2.6");
            ac.append("5.0");
        }
        ts.append(']');
        sp.append(']');
        ac.append(']');
        String body = "{\"client_upload_id\":\"" + uploadId + "\",\"started_at\":\""
                + java.time.Instant.ofEpochMilli(base) + "\",\"polyline\":\""
                + PolylineCodec.encode(points) + "\",\"timestamps\":" + ts + ",\"speeds\":" + sp
                + ",\"accuracies\":" + ac + "}";
        return mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createCourse(String token, long crewId, List<LatLng> course, LatLng finish)
            throws Exception {
        String body = "{\"name\":\"직선 코스\",\"route_polyline\":\"" + PolylineCodec.encode(course)
                + "\",\"start_lat\":" + course.get(0).lat() + ",\"start_lng\":" + course.get(0).lng()
                + ",\"finish_lat\":" + finish.lat() + ",\"finish_lng\":" + finish.lng() + "}";
        return json(mvc.perform(post("/api/v1/crews/" + crewId + "/courses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long createSession(String token, long crewId, long courseId) throws Exception {
        String body = "{\"course_id\":" + courseId + ",\"scheduled_at\":\"2026-07-10T21:00:00Z\","
                + "\"upload_deadline\":\"2026-07-11T09:00:00Z\"}";
        return json(mvc.perform(post("/api/v1/crews/" + crewId + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions cmd(String token, long sessionId,
            String action) throws Exception {
        return mvc.perform(post("/api/v1/sessions/" + sessionId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String login(String stub, String nickname) throws Exception {
        JsonNode l = json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kakao_access_token\":\"" + stub + "\"}")).andReturn());
        String token = l.get("access_token").asText();
        mvc.perform(put("/api/v1/users/me/nickname").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk());
        return token;
    }

    private long userId(String token) throws Exception {
        return json(mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn()).get("id").asLong();
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
        mvc.perform(post("/api/v1/crews/join").header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + invite.get("code").asText() + "\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
