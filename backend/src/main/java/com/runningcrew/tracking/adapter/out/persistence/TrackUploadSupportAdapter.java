package com.runningcrew.tracking.adapter.out.persistence;

import com.runningcrew.tracking.application.port.out.TrackUploadSupportPort;
import com.runningcrew.tracking.domain.CourseShape;
import com.runningcrew.tracking.domain.TrackCoord;
import com.runningcrew.tracking.domain.TrackPolylineCodec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link TrackUploadSupportPort} 구현 — race_session·participation·course·race_result를 <b>네이티브 SQL</b>로
 * 조회한다(race 컨텍스트 클래스 미참조 — ArchUnit R-2). 블롭 미접근.
 */
@Repository
public class TrackUploadSupportAdapter implements TrackUploadSupportPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<String> findSessionStatus(Long sessionId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT status FROM race_session WHERE id = ?1")
                .setParameter(1, sessionId)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of((String) rows.get(0));
    }

    @Override
    public boolean isActiveCrewMember(Long sessionId, Long userId) {
        Number count = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM race_session rs "
                                + "JOIN crew_member cm ON cm.crew_id = rs.crew_id "
                                + "WHERE rs.id = ?1 AND cm.user_id = ?2 AND cm.status = 'ACTIVE'")
                .setParameter(1, sessionId)
                .setParameter(2, userId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public boolean participationExists(Long sessionId, Long userId) {
        Number count = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM participation WHERE session_id = ?1 AND user_id = ?2")
                .setParameter(1, sessionId)
                .setParameter(2, userId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public Optional<CourseShape> findCourseShape(Long sessionId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT c.route_polyline, c.finish_lat, c.finish_lng, c.distance_m "
                                + "FROM course c JOIN race_session rs ON rs.course_id = c.id "
                                + "WHERE rs.id = ?1")
                .setParameter(1, sessionId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = (Object[]) rows.get(0);
        String polyline = (String) r[0];
        double finishLat = ((Number) r[1]).doubleValue();
        double finishLng = ((Number) r[2]).doubleValue();
        int distanceM = ((Number) r[3]).intValue();
        List<TrackCoord> coords = TrackPolylineCodec.decode(polyline);
        return Optional.of(new CourseShape(coords, new TrackCoord(finishLat, finishLng), distanceM));
    }

    @Override
    public boolean resultExists(Long sessionId) {
        Number count = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM race_result WHERE session_id = ?1")
                .setParameter(1, sessionId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
