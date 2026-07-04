package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.view.SessionSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RaceSessionJpaRepository extends JpaRepository<RaceSessionJpaEntity, Long> {

    // 목록(계약 §2): course 조인으로 course_name, 참가 행 수 서브쿼리, scheduled_at 내림차순.
    @Query(value = "select new com.runningcrew.race.application.view.SessionSummaryView("
            + "s.id, s.crewId, s.courseId, c.name, s.status, s.scheduledAt, s.uploadDeadline, "
            + "(select count(p) from ParticipationJpaEntity p where p.sessionId = s.id)) "
            + "from RaceSessionJpaEntity s, CourseJpaEntity c "
            + "where s.courseId = c.id and s.crewId = :crewId "
            + "order by s.scheduledAt desc, s.id desc",
            countQuery = "select count(s) from RaceSessionJpaEntity s where s.crewId = :crewId")
    Page<SessionSummaryView> findSummariesByCrewId(@Param("crewId") Long crewId, Pageable pageable);
}
