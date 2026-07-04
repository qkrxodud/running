package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.SessionQueryPort;
import com.runningcrew.race.application.view.SessionDetailView;
import com.runningcrew.race.application.view.SessionDetailView.CourseSummary;
import com.runningcrew.race.application.view.SessionDetailView.ParticipantView;
import com.runningcrew.race.application.view.SessionSummaryView;
import com.runningcrew.race.domain.ParticipationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * {@link SessionQueryPort} 구현. 참가자 nickname은 {@code user} 테이블 <b>네이티브 SQL 조인</b>으로
 * 해석한다(탈퇴 유저는 nickname이 이미 "탈퇴한 러너"로 익명화 — RS-B7). user 도메인 클래스 미참조(R-2).
 */
@Repository
public class SessionQueryAdapter implements SessionQueryPort {

    private final RaceSessionJpaRepository sessionJpa;
    private final CourseJpaRepository courseJpa;
    private final jakarta.persistence.EntityManager em;

    public SessionQueryAdapter(RaceSessionJpaRepository sessionJpa,
                               CourseJpaRepository courseJpa,
                               jakarta.persistence.EntityManager em) {
        this.sessionJpa = sessionJpa;
        this.courseJpa = courseJpa;
        this.em = em;
    }

    @Override
    public Page<SessionSummaryView> findByCrew(Long crewId, Pageable pageable) {
        return sessionJpa.findSummariesByCrewId(crewId, pageable);
    }

    @Override
    public Optional<SessionDetailView> findDetail(Long sessionId) {
        return sessionJpa.findById(sessionId).map(s -> {
            CourseJpaEntity c = courseJpa.findById(s.getCourseId())
                    .orElseThrow(() -> new IllegalStateException(
                            "세션이 참조하는 코스가 없습니다: " + s.getCourseId()));
            CourseSummary course = new CourseSummary(c.getId(), c.getName(), c.getDistanceM(),
                    c.getRoutePolyline(), c.getStartLat(), c.getStartLng(),
                    c.getFinishLat(), c.getFinishLng());
            return new SessionDetailView(s.getId(), s.getCrewId(), s.getStatus(),
                    s.getScheduledAt(), s.getUploadDeadline(), course, findParticipants(sessionId));
        });
    }

    private List<ParticipantView> findParticipants(Long sessionId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT p.user_id, u.nickname, p.status "
                                + "FROM participation p JOIN `user` u ON u.id = p.user_id "
                                + "WHERE p.session_id = ?1 ORDER BY p.id ASC")
                .setParameter(1, sessionId)
                .getResultList();
        return rows.stream().map(o -> {
            Object[] r = (Object[]) o;
            return new ParticipantView(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    ParticipationStatus.valueOf((String) r[2]));
        }).toList();
    }
}
