package com.runningcrew.ranking.adapter.out.persistence;

import com.runningcrew.ranking.application.port.out.LoadResultPort;
import com.runningcrew.ranking.application.view.ResultView;
import com.runningcrew.ranking.application.view.ResultView.EntryRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link LoadResultPort} 구현 — 네이티브 SQL. rank_entry·race_result·participation·user·track_record(요약)·
 * course를 조인하되 <b>track_payload는 조인하지 않는다</b>(블롭 격리 — TR-3). race/user 클래스 미참조(R-2).
 * 정렬: 완주(rank↑) → DNF → DNS, 동상태는 user_id↑(결정적).
 */
@Repository
public class ResultQueryAdapter implements LoadResultPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean sessionExists(Long sessionId) {
        Number c = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM race_session WHERE id = ?1")
                .setParameter(1, sessionId)
                .getSingleResult();
        return c.longValue() > 0;
    }

    @Override
    public boolean isCrewMember(Long sessionId, Long userId) {
        Number c = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM crew_member cm "
                                + "JOIN race_session rs ON rs.crew_id = cm.crew_id "
                                + "WHERE rs.id = ?1 AND cm.user_id = ?2 AND cm.status = 'ACTIVE'")
                .setParameter(1, sessionId)
                .setParameter(2, userId)
                .getSingleResult();
        return c.longValue() > 0;
    }

    @Override
    public boolean isCancelled(Long sessionId) {
        Number c = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM race_session WHERE id = ?1 AND status = 'CANCELLED'")
                .setParameter(1, sessionId)
                .getSingleResult();
        return c.longValue() > 0;
    }

    @Override
    public Optional<ResultView> findResult(Long sessionId) {
        List<?> header = em.createNativeQuery(
                        "SELECT rr.finalized_at, c.id, c.name, c.distance_m "
                                + "FROM race_result rr "
                                + "JOIN race_session rs ON rs.id = rr.session_id "
                                + "JOIN course c ON c.id = rs.course_id "
                                + "WHERE rr.session_id = ?1")
                .setParameter(1, sessionId)
                .getResultList();
        if (header.isEmpty()) {
            return Optional.empty();   // 미확정 → RESULT_NOT_READY
        }
        Object[] h = (Object[]) header.get(0);
        Instant finalizedAt = toInstant(h[0]);
        Long courseId = ((Number) h[1]).longValue();
        String courseName = (String) h[2];
        int courseDistanceM = ((Number) h[3]).intValue();

        List<?> rows = em.createNativeQuery(
                        "SELECT re.user_id, u.nickname, p.status, re.`rank`, re.record_time_s, "
                                + "re.is_pb, tr.total_distance_m "
                                + "FROM rank_entry re "
                                + "JOIN race_result rr ON rr.id = re.result_id "
                                + "JOIN participation p ON p.session_id = rr.session_id "
                                + "  AND p.user_id = re.user_id "
                                + "JOIN `user` u ON u.id = re.user_id "
                                + "LEFT JOIN track_record tr ON tr.session_id = rr.session_id "
                                + "  AND tr.user_id = re.user_id "
                                + "WHERE rr.session_id = ?1 "
                                + "ORDER BY CASE p.status WHEN 'FINISHED' THEN 0 "
                                + "  WHEN 'DNF' THEN 1 ELSE 2 END, "
                                + "  (re.`rank` IS NULL), re.`rank` ASC, re.user_id ASC")
                .setParameter(1, sessionId)
                .getResultList();

        List<EntryRow> entries = rows.stream().map(o -> {
            Object[] r = (Object[]) o;
            Long userId = ((Number) r[0]).longValue();
            String nickname = (String) r[1];
            String status = (String) r[2];
            Integer rank = r[3] != null ? ((Number) r[3]).intValue() : null;
            Integer recordTimeS = r[4] != null ? ((Number) r[4]).intValue() : null;
            boolean isPb = toBool(r[5]);
            Integer totalDistanceM = r[6] != null ? ((Number) r[6]).intValue() : null;
            return new EntryRow(userId, nickname, status, rank, recordTimeS, totalDistanceM, isPb);
        }).toList();

        return Optional.of(new ResultView(sessionId, courseId, courseName, courseDistanceM,
                finalizedAt, entries));
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

    private static boolean toBool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o instanceof Number n && n.intValue() != 0;
    }
}
