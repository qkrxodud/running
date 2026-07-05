package com.runningcrew.race.application;

import com.runningcrew.notification.application.port.out.NotificationSender;
import com.runningcrew.notification.domain.NotificationMessage;
import com.runningcrew.notification.domain.NotificationMessage.NotificationType;
import com.runningcrew.race.application.port.out.SessionReminderPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 세션 리마인더 발송(M3-C). 예정 임박 OPEN 세션에 "곧 시작" 알림을 세션당 1회 발송한다. 발송 로직은 clock
 * 주입({@code now})으로 테스트 재현 가능 — 스케줄러가 {@code clock.instant()}를 넘긴다. 딥링크는 세션 상세
 * (conventions §10). 실 FCM은 {@link NotificationSender} 어댑터 교체로 활성(스텁=구조화 로그).
 */
@Service
public class SessionReminderService {

    private final SessionReminderPort reminderPort;
    private final NotificationSender notificationSender;
    private final int leadMinutes;

    public SessionReminderService(SessionReminderPort reminderPort,
                                  NotificationSender notificationSender,
                                  @Value("${reminder.lead-minutes:30}") int leadMinutes) {
        this.reminderPort = reminderPort;
        this.notificationSender = notificationSender;
        this.leadMinutes = leadMinutes;
    }

    /** now 기준 임박 세션에 리마인더 발송. 멱등(reminder_notified_at) — 반환값=발송 건수. */
    public int sendDueReminders(Instant now) {
        int sent = 0;
        for (Long sessionId : reminderPort.findDueSessionIds(now, leadMinutes)) {
            if (!reminderPort.markReminderNotifiedIfFirst(sessionId, now)) {
                continue;   // 경쟁·중복 — no-op
            }
            List<Long> recipients = reminderPort.participantUserIds(sessionId);
            notificationSender.send(new NotificationMessage(
                    NotificationType.SESSION_REMINDER, sessionId, recipients,
                    "곧 레이스가 시작돼요", "예정된 러닝 세션이 곧 시작됩니다.",
                    Map.of("deep_link", "runningcrew://session/" + sessionId)));
            sent++;
        }
        return sent;
    }
}
