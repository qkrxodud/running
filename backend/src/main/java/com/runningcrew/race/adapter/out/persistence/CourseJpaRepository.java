package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.view.CourseSummaryView;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CourseJpaRepository extends JpaRepository<CourseJpaEntity, Long> {

    // 목록(계약 §2): 폴리라인 미포함 경량 요약, created_at 내림차순.
    @Query("select new com.runningcrew.race.application.view.CourseSummaryView("
            + "c.id, c.crewId, c.name, c.distanceM, c.createdAt) "
            + "from CourseJpaEntity c where c.crewId = :crewId order by c.createdAt desc, c.id desc")
    Page<CourseSummaryView> findSummariesByCrewId(@Param("crewId") Long crewId, Pageable pageable);

    @Query("select c.crewId from CourseJpaEntity c where c.id = :id")
    Optional<Long> findCrewIdById(@Param("id") Long id);
}
