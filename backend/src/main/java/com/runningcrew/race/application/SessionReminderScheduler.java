package com.runningcrew.race.application;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 리마인더 폴링 스케줄러(M3-C) — 마감 스케줄러(A9)와 별개. 주기적으로 {@code clock.instant()}로
 * {@link SessionReminderService}를 호출한다. 발송 판정·멱등은 서비스가 담당(clock 주입 테스트 가능).
 */
@Component
public class SessionReminderScheduler {

    private final SessionReminderService reminderService;
    private final Clock clock;

    public SessionReminderScheduler(SessionReminderService reminderService, Clock clock) {
        this.reminderService = reminderService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${reminder.poll-interval-ms:60000}")
    public void pollAndSend() {
        reminderService.sendDueReminders(clock.instant());
    }
}
