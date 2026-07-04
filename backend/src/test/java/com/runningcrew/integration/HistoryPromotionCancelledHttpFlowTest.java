package com.runningcrew.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M2-C 라이브 곡선 박제(C4·C6·C7): 확정 세션(히스토리 rank·PB) + CANCELLED 세션(업로드→취소→히스토리 배지
 * →결과 404→승격) + 승격 자격 게이트(비멤버 403·타인 트랙 403·DNF 409). track_payload 조인 없이 히스토리 조회.
 */
@AutoConfigureMockMvc
class HistoryPromotionCancelledHttpFlowTest extends AbstractMySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 확정_PB_취소보존_배지_승격_자격게이트() throws Exception {
        String leader = login("stub:mc-leader", "리더");
        long leaderId = userId(leader);
        long crewId = createCrew(leader, "M2C 크루");
        String member = login("stub:mc-member", "멤버");
        long memberId = userId(member);
        joinViaLeaderInvite(leader, crewId, member);

        // 코스: 직선 21점(약 1.6km — 승격 하한 1km 통과).
        List<LatLng> course = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            course.add(new LatLng(37.5000, 127.0000 + i * 0.0009));
        }
        LatLng finish = course.get(course.size() - 1);
        long courseId = createCourse(leader, crewId, course, finish);

        // ── 확정 세션(단독 참가 → 전원 업로드로 자동 확정) ──
        long doneSession = createSession(leader, crewId, courseId,
                "2026-07-10T21:00:00Z", "2026-07-11T09:00:00Z");
        cmd(leader, doneSession, "open").andExpect(status().isOk());
        cmd(leader, doneSession, "register").andExpect(status().isOk());
        cmd(leader, doneSession, "start").andExpect(status().isOk());
        long base1 = System.currentTimeMillis() - 7_200_000L;
        List<LatLng> finTrack = withFinishPad(course, finish);
        upload(leader, doneSession, "done-leader", base1, finTrack).andExpect(status().isCreated())
                .andExpect(jsonPath("$.finish_status").value("FINISHED"));
        // 단독 참가 전원 업로드 → 자동 확정
        mvc.perform(get("/api/v1/sessions/" + doneSession).header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        // ── CANCELLED 세션(2명 참가로 미확정 유지 → 리더 업로드 후 취소) ──
        long cancelSession = createSession(leader, crewId, courseId,
                "2026-07-12T21:00:00Z", "2026-07-13T09:00:00Z");
        cmd(leader, cancelSession, "open").andExpect(status().isOk());
        cmd(leader, cancelSession, "register").andExpect(status().isOk());
        cmd(member, cancelSession, "register").andExpect(status().isOk());
        cmd(leader, cancelSession, "start").andExpect(status().isOk());
        cmd(member, cancelSession, "start").andExpect(status().isOk());
        long base2 = System.currentTimeMillis() - 3_600_000L;
        // 리더만 업로드(멤버 미업로드 → 미확정 유지). CANCELLED 세션 수락 확인은 취소 후 재검(C7).
        upload(leader, cancelSession, "cancel-leader", base2, finTrack).andExpect(status().isCreated())
                .andExpect(jsonPath("$.finish_status").value("FINISHED"));
        long cancelTrackId = json(mvc.perform(get("/api/v1/sessions/" + cancelSession + "/track/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))).andReturn())
                .get("track_record_id").asLong();
        // 리더가 세션 취소(RUNNING → CANCELLED). 리더 트랙은 보존.
        cmd(leader, cancelSession, "cancel").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // C7: CANCELLED 세션 결과 조회 → 404 (대기 아님 — RaceResult 미생성)
        mvc.perform(get("/api/v1/sessions/" + cancelSession + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isNotFound());

        // C4: 내 기록 히스토리 — 확정(rank·pb) + 취소(배지) 최신순
        JsonNode records = json(mvc.perform(get("/api/v1/me/records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(2))
                .andReturn());
        JsonNode items = records.get("items");
        assertThat(items).hasSize(2);
        // 최신순: cancelSession(2026-07-12) 먼저
        JsonNode cancelled = items.get(0);
        assertThat(cancelled.get("session_id").asLong()).isEqualTo(cancelSession);
        assertThat(cancelled.get("session_cancelled").asBoolean()).isTrue();
        assertThat(cancelled.get("finish_status").asText()).isEqualTo("FINISHED");
        assertThat(cancelled.has("rank")).isFalse();                 // 취소 세션 rank 미산정(null→생략)
        assertThat(cancelled.get("is_pb").asBoolean()).isFalse();
        assertThat(cancelled.get("total_distance_m").asInt()).isGreaterThan(0);   // 거리 보존
        JsonNode confirmed = items.get(1);
        assertThat(confirmed.get("session_id").asLong()).isEqualTo(doneSession);
        assertThat(confirmed.get("session_cancelled").asBoolean()).isFalse();
        assertThat(confirmed.get("rank").asInt()).isEqualTo(1);
        assertThat(confirmed.get("is_pb").asBoolean()).isTrue();     // 첫 완주 → PB

        // C4: 코스별 PB — 확정 세션의 완주만(취소 세션 제외)
        JsonNode pbs = json(mvc.perform(get("/api/v1/me/personal-bests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))   // 코스 1개(확정 세션만)
                .andReturn());
        JsonNode pb = pbs.get("items").get(0);
        assertThat(pb.get("course_id").asLong()).isEqualTo(courseId);
        assertThat(pb.get("achieved_session_id").asLong()).isEqualTo(doneSession);
        assertThat(pb.get("best_record_time_s").asInt()).isGreaterThan(0);
        assertThat(pb.get("avg_pace_s_per_km").asInt()).isGreaterThan(0);

        // C6: 승격 — CANCELLED 세션의 본인 FINISHED 트랙도 승격 가능(설계 §8)
        JsonNode promoted = json(mvc.perform(post("/api/v1/crews/" + crewId + "/courses/promote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_track_record_id\":" + cancelTrackId
                                + ",\"name\":\"내가 뛴 코스\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.distance_m").isNumber())      // 서버 재확정
                .andExpect(jsonPath("$.created_by").value((int) leaderId))
                .andReturn());
        assertThat(promoted.get("distance_m").asInt()).isGreaterThanOrEqualTo(1000);

        // C6 게이트: 비멤버(아웃사이더) 승격 → 403
        String outsider = login("stub:mc-outsider", "아웃사이더");
        mvc.perform(post("/api/v1/crews/" + crewId + "/courses/promote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_track_record_id\":" + cancelTrackId + ",\"name\":\"침입\"}"))
                .andExpect(status().isForbidden());

        // C6 게이트: 타인 트랙 승격(멤버가 리더 트랙) → 403(존재 누설 방지)
        assertThat(memberId).isNotEqualTo(leaderId);
        mvc.perform(post("/api/v1/crews/" + crewId + "/courses/promote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_track_record_id\":" + cancelTrackId + ",\"name\":\"남의것\"}"))
                .andExpect(status().isForbidden());

        // C6 게이트: DNF 트랙 승격 → 409 COURSE_PROMOTION_INELIGIBLE
        long dnfSession = createSession(leader, crewId, courseId,
                "2026-07-14T21:00:00Z", "2026-07-15T09:00:00Z");
        cmd(leader, dnfSession, "open").andExpect(status().isOk());
        cmd(leader, dnfSession, "register").andExpect(status().isOk());
        cmd(member, dnfSession, "register").andExpect(status().isOk());   // 미확정 유지용
        cmd(leader, dnfSession, "start").andExpect(status().isOk());
        cmd(member, dnfSession, "start").andExpect(status().isOk());
        long base3 = System.currentTimeMillis() - 1_800_000L;
        List<LatLng> shortcut = new ArrayList<>(course.subList(0, 11));   // 지름길 → DNF
        upload(leader, dnfSession, "dnf-leader", base3, shortcut).andExpect(status().isCreated())
                .andExpect(jsonPath("$.finish_status").value("DNF"));
        long dnfTrackId = json(mvc.perform(get("/api/v1/sessions/" + dnfSession + "/track/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))).andReturn())
                .get("track_record_id").asLong();
        mvc.perform(post("/api/v1/crews/" + crewId + "/courses/promote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_track_record_id\":" + dnfTrackId + ",\"name\":\"미완주\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_PROMOTION_INELIGIBLE"));
    }

    // --- helpers ---

    private static List<LatLng> withFinishPad(List<LatLng> course, LatLng finish) {
        List<LatLng> t = new ArrayList<>(course);
        for (int i = 0; i < 3; i++) {
            t.add(finish);
        }
        return t;
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, long sessionId,
            String uploadId, long base, List<LatLng> points) throws Exception {
        int n = points.size();
        long[] ts = new long[n];
        StringBuilder speeds = new StringBuilder("[");
        StringBuilder accs = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            ts[i] = base + i * 30_000L;
            if (i > 0) {
                speeds.append(',');
                accs.append(',');
            }
            speeds.append("2.6");
            accs.append("5.0");
        }
        speeds.append(']');
        accs.append(']');
        StringBuilder tsArr = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                tsArr.append(',');
            }
            tsArr.append(ts[i]);
        }
        tsArr.append(']');
        String body = "{\"client_upload_id\":\"" + uploadId + "\",\"started_at\":\""
                + java.time.Instant.ofEpochMilli(base) + "\",\"polyline\":\""
                + PolylineCodec.encode(points) + "\",\"timestamps\":" + tsArr
                + ",\"speeds\":" + speeds + ",\"accuracies\":" + accs + "}";
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

    private long createSession(String token, long crewId, long courseId, String scheduledAt,
            String uploadDeadline) throws Exception {
        String body = "{\"course_id\":" + courseId + ",\"scheduled_at\":\"" + scheduledAt
                + "\",\"upload_deadline\":\"" + uploadDeadline + "\"}";
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
