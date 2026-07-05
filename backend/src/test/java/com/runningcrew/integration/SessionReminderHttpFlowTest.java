package com.runningcrew.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.notification.application.port.out.NotificationSender;
import com.runningcrew.notification.domain.NotificationMessage;
import com.runningcrew.notification.domain.NotificationMessage.NotificationType;
import com.runningcrew.race.application.SessionReminderService;
import com.runningcrew.race.domain.LatLng;
import com.runningcrew.race.domain.PolylineCodec;
import com.runningcrew.support.AbstractMySqlIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M3-C 리마인더 발송 seed: clock 주입({@code now})으로 예정 임박 OPEN 세션에 세션당 1회 발송(멱등 —
 * 2회 호출해도 재발송 0). FCM data에 세션 딥링크(§10). 스케줄러는 서비스를 호출만(clock 위임).
 */
@AutoConfigureMockMvc
class SessionReminderHttpFlowTest extends AbstractMySqlIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired SessionReminderService reminderService;
    @MockitoSpyBean NotificationSender notificationSender;

    @Test
    void 임박_OPEN_세션_리마인더_세션당_1회_딥링크() throws Exception {
        String leader = login("stub:rm-leader", "리더");
        long crewId = createCrew(leader, "리마인더 크루");
        List<LatLng> course = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            course.add(new LatLng(37.5000, 127.0000 + i * 0.0009));
        }
        long courseId = createCourse(leader, crewId, course);
        // scheduled_at = 주입 now(00:00)로부터 20분 뒤(리드 30분 내) → 임박. (실 now 대비 과거라 실 스케줄러 무간섭)
        long sessionId = createSession(leader, crewId, courseId,
                "2026-07-05T00:20:00Z", "2026-07-05T12:00:00Z");
        cmd(leader, sessionId, "open").andExpect(status().isOk());
        cmd(leader, sessionId, "register").andExpect(status().isOk());

        Instant now = Instant.parse("2026-07-05T00:00:00Z");

        int sent1 = reminderService.sendDueReminders(now);
        assertThat(sent1).isGreaterThanOrEqualTo(1);

        // 딥링크·타입 검증(§10: runningcrew://session/{id})
        verify(notificationSender, times(1)).send(argThat((NotificationMessage m) ->
                m.type() == NotificationType.SESSION_REMINDER
                        && ("runningcrew://session/" + sessionId).equals(m.deepLink())
                        && sessionId == m.sessionId()));

        // 2회차 — 이미 발송(reminder_notified_at set) → 재발송 0(멱등)
        int sent2 = reminderService.sendDueReminders(now);
        assertThat(sent2).isZero();
        verify(notificationSender, times(1)).send(argThat((NotificationMessage m) ->
                m.type() == NotificationType.SESSION_REMINDER && sessionId == m.sessionId()));
    }

    private long createCourse(String token, long crewId, List<LatLng> course) throws Exception {
        LatLng finish = course.get(course.size() - 1);
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
