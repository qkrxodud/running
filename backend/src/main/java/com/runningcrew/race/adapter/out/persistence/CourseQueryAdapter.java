package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.CourseQueryPort;
import com.runningcrew.race.application.view.CourseDetailView;
import com.runningcrew.race.application.view.CourseSummaryView;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** {@link CourseQueryPort} 구현(읽기 모델). */
@Repository
public class CourseQueryAdapter implements CourseQueryPort {

    private final CourseJpaRepository jpa;

    public CourseQueryAdapter(CourseJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<CourseDetailView> findDetail(Long courseId) {
        return jpa.findById(courseId).map(c -> new CourseDetailView(
                c.getId(), c.getCrewId(), c.getName(), c.getRoutePolyline(), c.getDistanceM(),
                c.getStartLat(), c.getStartLng(), c.getFinishLat(), c.getFinishLng(),
                c.getCreatedBy(), c.getCreatedAt()));
    }

    @Override
    public Page<CourseSummaryView> findByCrew(Long crewId, Pageable pageable) {
        return jpa.findSummariesByCrewId(crewId, pageable);
    }

    @Override
    public Optional<Long> findCrewId(Long courseId) {
        return jpa.findCrewIdById(courseId);
    }
}
