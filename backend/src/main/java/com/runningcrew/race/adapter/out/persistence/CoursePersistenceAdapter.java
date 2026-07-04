package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.CourseRepository;
import com.runningcrew.race.domain.Course;
import org.springframework.stereotype.Repository;

/** {@link CourseRepository} 구현 — 도메인 {@link Course} ↔ {@link CourseJpaEntity} 매핑(저장만). */
@Repository
public class CoursePersistenceAdapter implements CourseRepository {

    private final CourseJpaRepository jpa;

    public CoursePersistenceAdapter(CourseJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Course save(Course course) {
        CourseJpaEntity saved = jpa.saveAndFlush(new CourseJpaEntity(
                course.getId(),
                course.getCrewId(),
                course.getName(),
                course.getRoutePolyline(),
                course.getDistanceM(),
                course.getStartPoint().lat(),
                course.getStartPoint().lng(),
                course.getFinishPoint().lat(),
                course.getFinishPoint().lng(),
                course.getCreatedBy(),
                course.getCreatedAt()));
        return new Course(saved.getId(), saved.getCrewId(), saved.getName(),
                course.getRoutePath(), saved.getDistanceM(),
                course.getStartPoint(), course.getFinishPoint(),
                saved.getCreatedBy(), saved.getCreatedAt());
    }
}
