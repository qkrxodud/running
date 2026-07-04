package com.runningcrew.race.application.port.out;

import com.runningcrew.race.domain.Course;

/**
 * Course 애그리거트 쓰기 out-port. 불변 애그리거트라 저장만 제공(수정/삭제 없음 — CO-B5).
 */
public interface CourseRepository {

    Course save(Course course);
}
