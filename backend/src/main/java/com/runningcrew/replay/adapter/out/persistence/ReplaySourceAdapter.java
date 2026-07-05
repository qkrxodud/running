package com.runningcrew.replay.adapter.out.persistence;

import com.runningcrew.replay.application.port.out.ReplaySourcePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link ReplaySourcePort} 구현 — 스냅샷 생성의 <b>명시적 payload 소비자</b>(RP-1). track_record + track_payload
 * (refined) + course를 네이티브 SQL로 로드한다. 이 어댑터만 track_payload를 접근하고, 순위/결과/히스토리
 * 조회 어댑터엔 미주입(격리 유지). tracking/ranking 클래스 미참조(RP-2, 네이티브 SQL). finish_status는
 * 파생(finished_at 존재=FINISHED).
 */
@Repository
public class ReplaySourceAdapter implements ReplaySourcePort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<GenerationSource> loadGenerationSource(Long sessionId) {
        List<?> courseRows = em.createNativeQuery(
                        "SELECT c.distance_m, c.route_polyline, c.start_lat, c.start_lng, "
                                + "c.finish_lat, c.finish_lng "
                                + "FROM course c JOIN race_session rs ON rs.course_id = c.id "
                                + "WHERE rs.id = ?1")
                .setParameter(1, sessionId)
                .getResultList();
        if (courseRows.isEmpty()) {
            return Optional.empty();
        }
        Object[] c = (Object[]) courseRows.get(0);
        int distanceM = ((Number) c[0]).intValue();
        String polyline = (String) c[1];
        double startLat = ((Number) c[2]).doubleValue();
        double startLng = ((Number) c[3]).doubleValue();
        double finishLat = ((Number) c[4]).doubleValue();
        double finishLng = ((Number) c[5]).doubleValue();

        List<?> rows = em.createNativeQuery(
                        "SELECT tr.user_id, tr.started_at, tr.finished_at, tp.refined_payload "
                                + "FROM track_record tr "
                                + "JOIN track_payload tp ON tp.track_record_id = tr.id "
                                + "WHERE tr.session_id = ?1 "
                                + "ORDER BY tr.user_id ASC")
                .setParameter(1, sessionId)
                .getResultList();

        List<ParticipantSource> participants = new ArrayList<>(rows.size());
        for (Object o : rows) {
            Object[] r = (Object[]) o;
            long userId = ((Number) r[0]).longValue();
            long startedAtMillis = toInstant(r[1]).toEpochMilli();
            Long finishedAtMillis = r[2] != null ? toInstant(r[2]).toEpochMilli() : null;
            String finishStatus = finishedAtMillis != null ? "FINISHED" : "DNF";
            String refinedPayload = (String) r[3];
            if (refinedPayload == null) {
                continue;   // 정제 경로 부재(비정상) — 스킵
            }
            participants.add(new ParticipantSource(userId, startedAtMillis, finishedAtMillis,
                    finishStatus, refinedPayload));
        }
        return Optional.of(new GenerationSource(distanceM, polyline, startLat, startLng,
                finishLat, finishLng, participants));
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
