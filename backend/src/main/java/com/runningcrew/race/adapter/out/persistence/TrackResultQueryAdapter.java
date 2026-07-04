package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.TrackResultQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * {@link TrackResultQueryPort} 구현 — track_record <b>요약</b>을 네이티브 SQL로 읽는다(track_payload 조인
 * 0건 — TR-3). race가 tracking 클래스를 참조하지 않는다(ArchUnit R-2).
 */
@Repository
public class TrackResultQueryAdapter implements TrackResultQueryPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<TrackResult> findBySessionId(Long sessionId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT user_id, finished_at, total_time_s, total_distance_m "
                                + "FROM track_record WHERE session_id = ?1")
                .setParameter(1, sessionId)
                .getResultList();
        return rows.stream().map(o -> {
            Object[] r = (Object[]) o;
            Long userId = ((Number) r[0]).longValue();
            boolean finished = r[1] != null;
            Integer recordTimeS = r[2] != null ? ((Number) r[2]).intValue() : null;
            Integer totalDistanceM = r[3] != null ? ((Number) r[3]).intValue() : null;
            return new TrackResult(userId, finished, recordTimeS, totalDistanceM);
        }).toList();
    }
}
