package com.runningcrew.replay.adapter.out.persistence;

import com.runningcrew.replay.application.port.out.ReplayQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link ReplayQueryPort} 구현 — 조회 보조. race_session·crew_member·race_result·user를 네이티브 SQL로.
 * <b>track_payload 미접근</b>(조회 경로 격리 유지). 탈퇴 유저(status=WITHDRAWN)는 표시명 {@code "탈퇴한 러너"}
 * (RP-3 — 조회 시점 익명화). `user`는 예약어라 백틱 인용.
 */
@Repository
public class ReplayQueryAdapter implements ReplayQueryPort {

    private static final String WITHDRAWN_DISPLAY_NAME = "탈퇴한 러너";

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean sessionExists(Long sessionId) {
        Number c = (Number) em.createNativeQuery("SELECT COUNT(*) FROM race_session WHERE id = ?1")
                .setParameter(1, sessionId).getSingleResult();
        return c.longValue() > 0;
    }

    @Override
    public boolean isCrewMember(Long sessionId, Long userId) {
        Number c = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM crew_member cm "
                                + "JOIN race_session rs ON rs.crew_id = cm.crew_id "
                                + "WHERE rs.id = ?1 AND cm.user_id = ?2 AND cm.status = 'ACTIVE'")
                .setParameter(1, sessionId).setParameter(2, userId).getSingleResult();
        return c.longValue() > 0;
    }

    @Override
    public Optional<Instant> findFinalizedAt(Long sessionId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT finalized_at FROM race_result WHERE session_id = ?1")
                .setParameter(1, sessionId).getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(toInstant(rows.get(0)));
    }

    @Override
    public Map<Long, String> displayNames(List<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<?> rows = em.createNativeQuery(
                        "SELECT id, nickname, status FROM `user` WHERE id IN (:ids)")
                .setParameter("ids", userIds)
                .getResultList();
        for (Object o : rows) {
            Object[] r = (Object[]) o;
            Long id = ((Number) r[0]).longValue();
            String nickname = (String) r[1];
            String status = (String) r[2];
            result.put(id, "WITHDRAWN".equals(status) ? WITHDRAWN_DISPLAY_NAME : nickname);
        }
        return result;
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        return ((java.time.OffsetDateTime) o).toInstant();
    }
}
