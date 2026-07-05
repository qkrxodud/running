package com.runningcrew.race.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * 세션 리마인더 out-port(M3-C). 예정 시각이 임박한 OPEN 세션을 찾고, 참가자·멱등 마킹을 제공한다.
 * {@code reminder_notified_at} 원자적 set으로 재폴링·재기동에도 세션당 1회.
 */
public interface SessionReminderPort {

    /** now 기준 리드 윈도 내(now < scheduled_at ≤ now+lead)·OPEN·미발송 세션 id. */
    List<Long> findDueSessionIds(Instant now, int leadMinutes);

    /** 리마인더 수신 대상(참가 신청자 user_id). */
    List<Long> participantUserIds(Long sessionId);

    /**
     * {@code reminder_notified_at}이 NULL이면 now로 set하고 true(최초 발송), 이미 set이면 false(중복 no-op).
     * 원자적 UPDATE … WHERE reminder_notified_at IS NULL.
     */
    boolean markReminderNotifiedIfFirst(Long sessionId, Instant now);
}
