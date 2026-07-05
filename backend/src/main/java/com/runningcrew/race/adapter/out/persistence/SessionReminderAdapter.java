package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.SessionReminderPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SessionReminderPort} 구현 — 네이티브 SQL. 예정 임박 OPEN·미발송 세션 조회 + 참가자 + 원자적 마킹.
 */
@Repository
public class SessionReminderAdapter implements SessionReminderPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public List<Long> findDueSessionIds(Instant now, int leadMinutes) {
        Instant windowEnd = now.plus(leadMinutes, ChronoUnit.MINUTES);
        List<?> rows = em.createNativeQuery(
                        "SELECT id FROM race_session "
                                + "WHERE status = 'OPEN' AND reminder_notified_at IS NULL "
                                + "AND scheduled_at > ?1 AND scheduled_at <= ?2 "
                                + "ORDER BY id")
                .setParameter(1, java.sql.Timestamp.from(now))
                .setParameter(2, java.sql.Timestamp.from(windowEnd))
                .getResultList();
        return toLongs(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> participantUserIds(Long sessionId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT user_id FROM participation WHERE session_id = ?1 ORDER BY user_id")
                .setParameter(1, sessionId)
                .getResultList();
        return toLongs(rows);
    }

    @Override
    @Transactional
    public boolean markReminderNotifiedIfFirst(Long sessionId, Instant now) {
        int updated = em.createNativeQuery(
                        "UPDATE race_session SET reminder_notified_at = ?1 "
                                + "WHERE id = ?2 AND reminder_notified_at IS NULL")
                .setParameter(1, java.sql.Timestamp.from(now))
                .setParameter(2, sessionId)
                .executeUpdate();
        return updated == 1;
    }

    private static List<Long> toLongs(List<?> rows) {
        List<Long> ids = new ArrayList<>(rows.size());
        for (Object r : rows) {
            ids.add(((Number) r).longValue());
        }
        return ids;
    }
}
