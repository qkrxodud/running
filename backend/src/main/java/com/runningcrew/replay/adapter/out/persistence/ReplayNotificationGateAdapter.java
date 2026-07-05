package com.runningcrew.replay.adapter.out.persistence;

import com.runningcrew.replay.application.port.out.ReplayNotificationGate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ReplayNotificationGate} 구현 — {@code race_session.replay_notified_at} 원자적 check-and-set으로 FCM
 * 세션당 1회 멱등(RP-12). {@code UPDATE … WHERE replay_notified_at IS NULL}이 1행이면 최초(발송), 0행이면
 * 이미 발송(재생성·중복 — no-op). 컨텍스트 경계는 네이티브 SQL(RP-2).
 */
@Repository
public class ReplayNotificationGateAdapter implements ReplayNotificationGate {

    @PersistenceContext
    private EntityManager em;

    private final Clock clock;

    public ReplayNotificationGateAdapter(Clock clock) {
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean markNotifiedIfFirst(Long sessionId) {
        int updated = em.createNativeQuery(
                        "UPDATE race_session SET replay_notified_at = ?1 "
                                + "WHERE id = ?2 AND replay_notified_at IS NULL")
                .setParameter(1, java.sql.Timestamp.from(clock.instant()))
                .setParameter(2, sessionId)
                .executeUpdate();
        return updated == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> participantUserIds(Long sessionId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT user_id FROM participation WHERE session_id = ?1 ORDER BY user_id")
                .setParameter(1, sessionId)
                .getResultList();
        List<Long> ids = new ArrayList<>(rows.size());
        for (Object r : rows) {
            ids.add(((Number) r).longValue());
        }
        return ids;
    }
}
