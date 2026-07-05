package com.runningcrew.race.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.notification.application.port.out.NotificationSender;
import com.runningcrew.notification.domain.NotificationMessage;
import com.runningcrew.notification.domain.NotificationMessage.NotificationType;
import com.runningcrew.race.application.port.out.SessionReminderPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SessionReminderService} <b>정책 골든</b>(설계 72 §M3-C, 계획서 §8). 서비스는 순수 함수가 아니라
 * 포트 의존이지만 <b>clock(now) 주입 + 인메모리 fake 포트</b>로 결정적이다 — 발송 멱등·메시지 계약(딥링크
 * §10)·위임을 박제한다. <b>시각 윈도 경계(scheduled_at ≤ now+lead)의 SQL 판정은 어댑터 소관</b>
 * (SessionReminderAdapter) — 통합 seed({@code SessionReminderHttpFlowTest})가 커버, 여기선 서비스 정책만.
 */
class SessionReminderServicePolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-11T08:30:00Z");

    /** 발송 여부·멱등을 통제하는 인메모리 포트. */
    private static final class FakePort implements SessionReminderPort {
        List<Long> due = new ArrayList<>();
        Set<Long> alreadyNotified = new HashSet<>();
        Map<Long, List<Long>> participants = Map.of();
        Instant capturedNow;
        int capturedLead = -1;

        @Override
        public List<Long> findDueSessionIds(Instant now, int leadMinutes) {
            capturedNow = now;
            capturedLead = leadMinutes;
            return due;
        }

        @Override
        public List<Long> participantUserIds(Long sessionId) {
            return participants.getOrDefault(sessionId, List.of());
        }

        @Override
        public boolean markReminderNotifiedIfFirst(Long sessionId, Instant now) {
            return alreadyNotified.add(sessionId);   // 최초=true, 이미 있으면 false(멱등)
        }
    }

    private static final class CapturingSender implements NotificationSender {
        final List<NotificationMessage> sent = new ArrayList<>();

        @Override
        public void send(NotificationMessage message) {
            sent.add(message);
        }
    }

    @Test
    @DisplayName("발송 — 임박 세션에 SESSION_REMINDER 1건, 딥링크 runningcrew://session/{id}(§10)")
    void 발송_메시지_계약() {
        FakePort port = new FakePort();
        port.due = List.of(91L);
        port.participants = Map.of(91L, List.of(3L, 7L));
        CapturingSender sender = new CapturingSender();
        SessionReminderService service = new SessionReminderService(port, sender, 30);

        int sent = service.sendDueReminders(NOW);

        assertThat(sent).isEqualTo(1);
        assertThat(sender.sent).hasSize(1);
        NotificationMessage m = sender.sent.get(0);
        assertThat(m.type()).isEqualTo(NotificationType.SESSION_REMINDER);
        assertThat(m.sessionId()).isEqualTo(91L);
        assertThat(m.recipientUserIds()).containsExactly(3L, 7L);
        assertThat(m.deepLink()).isEqualTo("runningcrew://session/91");
    }

    @Test
    @DisplayName("멱등 — 이미 발송된(reminder_notified_at set) 세션은 재발송 안 함")
    void 멱등_재발송_금지() {
        FakePort port = new FakePort();
        port.due = List.of(91L);
        port.alreadyNotified.add(91L);   // 이미 발송됨
        CapturingSender sender = new CapturingSender();

        int sent = new SessionReminderService(port, sender, 30).sendDueReminders(NOW);

        assertThat(sent).isZero();
        assertThat(sender.sent).isEmpty();   // markIfFirst=false → no-op
    }

    @Test
    @DisplayName("여러 세션 중 미발송만 발송 — 멱등 세션은 건너뛴다")
    void 혼재_미발송만_발송() {
        FakePort port = new FakePort();
        port.due = List.of(91L, 92L, 93L);
        port.alreadyNotified.add(92L);   // 92는 이미 발송
        port.participants = Map.of(91L, List.of(1L), 93L, List.of(2L));
        CapturingSender sender = new CapturingSender();

        int sent = new SessionReminderService(port, sender, 30).sendDueReminders(NOW);

        assertThat(sent).isEqualTo(2);
        assertThat(sender.sent).extracting(NotificationMessage::sessionId)
                .containsExactly(91L, 93L);   // 92 건너뜀
    }

    @Test
    @DisplayName("clock(now)·lead 를 포트에 위임 — 시각 윈도 판정은 어댑터 소관(경계 명시)")
    void clock과_lead를_포트에_위임() {
        FakePort port = new FakePort();
        CapturingSender sender = new CapturingSender();

        new SessionReminderService(port, sender, 45).sendDueReminders(NOW);

        assertThat(port.capturedNow).isEqualTo(NOW);   // 주입된 now 그대로
        assertThat(port.capturedLead).isEqualTo(45);   // 외부화된 lead 전달
    }

    @Test
    @DisplayName("임박 세션 없으면 발송 0")
    void 임박_없으면_무발송() {
        FakePort port = new FakePort();   // due 비어 있음
        CapturingSender sender = new CapturingSender();
        assertThat(new SessionReminderService(port, sender, 30).sendDueReminders(NOW)).isZero();
        assertThat(sender.sent).isEmpty();
    }
}
