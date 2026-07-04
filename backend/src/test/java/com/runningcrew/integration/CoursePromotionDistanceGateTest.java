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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * C6 / PR-3 회귀 박제: 거리 하한 게이트. 하한을 100km로 외부화 override → 정상 FINISHED 트랙(≈1.6km)도
 * 미달 → 409 COURSE_PROMOTION_INELIGIBLE. 하한이 config 외부화(하드코딩 아님)임을 이 override 자체가 증명.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "promotion.min-distance-m=100000")
class CoursePromotionDistanceGateTest extends AbstractMySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void 거리_하한_미달_409() throws Exception {
        String leader = login("stub:gate-leader", "게이트");
        long crewId = createCrew(leader, "게이트 크루");
        List<LatLng> course = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            course.add(new LatLng(37.5000, 127.0000 + i * 0.0009));
        }
        LatLng finish = course.get(course.size() - 1);
        long courseId = createCourse(leader, crewId, course, finish);
        long session = createSession(leader, crewId, courseId);
        cmd(leader, session, "open").andExpect(status().isOk());
        cmd(leader, session, "register").andExpect(status().isOk());
        cmd(leader, session, "start").andExpect(status().isOk());

        long base = System.currentTimeMillis() - 3_600_000L;
        List<LatLng> finTrack = new ArrayList<>(course);
        for (int i = 0; i < 3; i++) {
            finTrack.add(finish);
        }
        upload(leader, session, "gate-1", base, finTrack).andExpect(status().isCreated())
                .andExpect(jsonPath("$.finish_status").value("FINISHED"));
        long trackId = json(mvc.perform(get("/api/v1/sessions/" + session + "/track/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))).andReturn())
                .get("track_record_id").asLong();

        // 하한 100km > 실거리(≈1.6km) → 409
        mvc.perform(post("/api/v1/crews/" + crewId + "/courses/promote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(leader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_track_record_id\":" + trackId + ",\"name\":\"짧은 코스\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_PROMOTION_INELIGIBLE"));
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

    private long createCrew(String token, String name) throws Exception {
        return json(mvc.perform(post("/api/v1/crews").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
