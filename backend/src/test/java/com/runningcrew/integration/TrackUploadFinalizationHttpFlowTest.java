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
 * M2-A "라이브 곡선" 박제(A2·A3·A6·A7·A8·A10): 2명 register→start → 합성 트랙 2개 업로드(1 완주 / 1 지름길 DNF)
 * → 전원 업로드로 <b>자동 확정</b>(AFTER_COMMIT 동기) → 결과 조회에서 순위·DNF 하단·PB 표시 확인. 멱등·오류도 함께.
 */
@AutoConfigureMockMvc
class TrackUploadFinalizationHttpFlowTest extends AbstractMySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 두명_업로드_자동확정_결과조회_순위_DNF_PB() throws Exception {
        String leader = loginNickname("stub:m2-leader", "리더");
        long leaderId = userId(leader);
        long crewId = createCrew(leader, "M2 크루");
        String member = loginNickname("stub:m2-member", "멤버");
        long memberId = userId(member);
        joinViaLeaderInvite(leader, crewId, member);

        // 코스: 직선 21점(약 1.6km). finish = 마지막 점.
        List<LatLng> course = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            course.add(new LatLng(37.5000, 127.0000 + i * 0.0009));
        }
        LatLng finish = course.get(course.size() - 1);
        String coursePolyline = PolylineCodec.encode(course);
        JsonNode courseJson = json(mvc.perform(post("/api/v1/crews/" + crewId + "/courses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseBody("직선 코스", coursePolyline, course.get(0), finish)))
                .andExpect(status().isCreated()).andReturn());
        long courseId = courseJson.get("id").asLong();

        // 세션 생성 → open → 둘 다 register → start(RUNNING)
        JsonNode session = json(mvc.perform(post("/api/v1/crews/" + crewId + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(courseId, "2026-07-10T21:00:00Z", "2026-07-11T09:00:00Z")))
                .andExpect(status().isCreated()).andReturn());
        long sessionId = session.get("id").asLong();
        cmd(leader, sessionId, "open").andExpect(status().isOk());
        cmd(leader, sessionId, "register").andExpect(status().isOk());
        cmd(member, sessionId, "register").andExpect(status().isOk());
        cmd(leader, sessionId, "start").andExpect(status().isOk());
        cmd(member, sessionId, "start").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        // 확정 전 결과 조회 → 409 RESULT_NOT_READY
        mvc.perform(get("/api/v1/sessions/" + sessionId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_READY"));

        long base = System.currentTimeMillis() - 3_600_000L;

        // 완주 트랙(리더): 코스 21점 + 도착점 3점(정지). 30s 간격.
        List<LatLng> finTrack = new ArrayList<>(course);
        for (int i = 0; i < 3; i++) {
            finTrack.add(finish);
        }
        long[] finTs = steps(base, finTrack.size(), 30_000L);
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("up-leader-1", base, PolylineCodec.encode(finTrack), finTs)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.finish_status").value("FINISHED"))
                .andExpect(jsonPath("$.finished_at").isNotEmpty())
                .andExpect(jsonPath("$.total_time_s").isNumber())
                .andExpect(jsonPath("$.gps_gap_count").value(0));

        // 상태 조회(리더) → FINISHED
        mvc.perform(get("/api/v1/sessions/" + sessionId + "/track/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finish_status").value("FINISHED"));

        // 멱등: 동일 client_upload_id 재요청 → 200. 다른 내용 → 409 TRACK_ALREADY_UPLOADED.
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("up-leader-1", base, PolylineCodec.encode(finTrack), finTs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finish_status").value("FINISHED"));
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("up-leader-2", base, PolylineCodec.encode(finTrack), finTs)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRACK_ALREADY_UPLOADED"));

        // 아직 멤버 미업로드 → 결과 미확정
        mvc.perform(get("/api/v1/sessions/" + sessionId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isConflict());

        // 지름길 DNF 트랙(멤버): 코스 앞 절반(11점)만 — 도착 미진입 + 거리 부족.
        List<LatLng> dnfTrack = new ArrayList<>(course.subList(0, 11));
        long[] dnfTs = steps(base, dnfTrack.size(), 30_000L);
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("up-member-1", base, PolylineCodec.encode(dnfTrack), dnfTs)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.finish_status").value("DNF"))
                .andExpect(jsonPath("$.finished_at").doesNotExist())
                .andExpect(jsonPath("$.total_distance_m").isNumber());

        // 전원 업로드 → 자동 확정(AFTER_COMMIT 동기) → 세션 COMPLETED
        mvc.perform(get("/api/v1/sessions/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 결과 조회 → 순위(완주 1위·PB) · DNF 하단(rank null)
        JsonNode result = json(mvc.perform(get("/api/v1/sessions/" + sessionId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.distance_m").isNumber())
                .andExpect(jsonPath("$.finalized_at").isNotEmpty())
                .andReturn());
        JsonNode entries = result.get("entries");
        assertThat(entries).hasSize(2);

        JsonNode first = entries.get(0);
        assertThat(first.get("user_id").asLong()).isEqualTo(leaderId);
        assertThat(first.get("status").asText()).isEqualTo("FINISHED");
        assertThat(first.get("rank").asInt()).isEqualTo(1);
        assertThat(first.get("is_pb").asBoolean()).isTrue();          // 첫 완주 → PB
        assertThat(first.get("record_time_s").isNull()).isFalse();
        assertThat(first.get("avg_pace_s_per_km").isNull()).isFalse();

        // non_null 직렬화라 null 필드는 응답에서 생략된다(부재 = null).
        JsonNode second = entries.get(1);
        assertThat(second.get("user_id").asLong()).isEqualTo(memberId);
        assertThat(second.get("status").asText()).isEqualTo("DNF");
        assertThat(second.has("rank")).isFalse();                     // DNF는 rank 미부여(생략)
        assertThat(second.get("is_pb").asBoolean()).isFalse();
        assertThat(second.has("record_time_s")).isFalse();
        assertThat(second.has("avg_pace_s_per_km")).isFalse();
        assertThat(second.get("total_distance_m").isNull()).isFalse();// DNF도 뛴 거리 보존

        // 비크루 아웃사이더 결과 조회 → 403
        String outsider = loginNickname("stub:m2-outsider", "아웃사이더");
        mvc.perform(get("/api/v1/sessions/" + sessionId + "/result")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());

        // 확정 후 업로드 → 409(세션 상태)
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("up-leader-9", base, PolylineCodec.encode(finTrack), finTs)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }

    @Test
    void 업로드_페이로드_검증_오류코드() throws Exception {
        String leader = loginNickname("stub:m2-val-leader", "검증리더");
        long crewId = createCrew(leader, "검증 크루");
        List<LatLng> course = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            course.add(new LatLng(37.5000, 127.0000 + i * 0.0009));
        }
        String coursePolyline = PolylineCodec.encode(course);
        long courseId = json(mvc.perform(post("/api/v1/crews/" + crewId + "/courses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseBody("코스", coursePolyline, course.get(0),
                                course.get(course.size() - 1))))
                .andReturn()).get("id").asLong();
        long sessionId = json(mvc.perform(post("/api/v1/crews/" + crewId + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody(courseId, "2026-07-10T21:00:00Z", "2026-07-11T09:00:00Z")))
                .andReturn()).get("id").asLong();
        cmd(leader, sessionId, "open").andExpect(status().isOk());
        cmd(leader, sessionId, "register").andExpect(status().isOk());
        cmd(leader, sessionId, "start").andExpect(status().isOk());

        long base = System.currentTimeMillis() - 3_600_000L;
        String poly = PolylineCodec.encode(course);   // 21점

        // TK-1 배열 길이 불일치 → 400 TRACK_ARRAY_LENGTH_MISMATCH (timestamps 20개)
        long[] shortTs = steps(base, 20, 30_000L);
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("v1", base, poly, shortTs)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRACK_ARRAY_LENGTH_MISMATCH"));

        // TK-3 폴리라인 <2점 → 400 TRACK_PAYLOAD_INVALID
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody("v2", base, PolylineCodec.encode(course.subList(0, 1)),
                                steps(base, 1, 30_000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRACK_PAYLOAD_INVALID"));

        // TK-4 client_meta 미허용 키 → 400 VALIDATION_ERROR
        long[] ts = steps(base, course.size(), 30_000L);
        String badMeta = "{\"client_upload_id\":\"v3\",\"started_at\":\""
                + java.time.Instant.ofEpochMilli(base) + "\",\"polyline\":\"" + poly + "\","
                + "\"timestamps\":" + arr(ts) + ",\"speeds\":" + speeds(ts.length)
                + ",\"accuracies\":" + accs(ts.length)
                + ",\"client_meta\":{\"os\":\"android\",\"hacker\":\"x\"}}";
        mvc.perform(post("/api/v1/sessions/" + sessionId + "/track")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON).content(badMeta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- payload builders ---

    private String uploadBody(String uploadId, long startedAtMillis, String polyline, long[] ts) {
        return "{\"client_upload_id\":\"" + uploadId + "\",\"started_at\":\""
                + java.time.Instant.ofEpochMilli(startedAtMillis) + "\",\"polyline\":\"" + polyline
                + "\",\"timestamps\":" + arr(ts) + ",\"speeds\":" + speeds(ts.length)
                + ",\"accuracies\":" + accs(ts.length)
                + ",\"client_meta\":{\"os\":\"android\",\"os_version\":\"14\","
                + "\"device_model\":\"SM-S911N\"}}";
    }

    private static long[] steps(long base, int n, long stepMs) {
        long[] ts = new long[n];
        for (int i = 0; i < n; i++) {
            ts[i] = base + i * stepMs;
        }
        return ts;
    }

    private static String arr(long[] ts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ts[i]);
        }
        return sb.append(']').toString();
    }

    private static String speeds(int n) {
        return filled(n, "2.6");
    }

    private static String accs(int n) {
        return filled(n, "5.0");
    }

    private static String filled(int n, String v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v);
        }
        return sb.append(']').toString();
    }

    // --- helpers (RaceSessionHttpFlowTest와 동형) ---

    private org.springframework.test.web.servlet.ResultActions cmd(String token, long sessionId,
                                                                   String action) throws Exception {
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

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
