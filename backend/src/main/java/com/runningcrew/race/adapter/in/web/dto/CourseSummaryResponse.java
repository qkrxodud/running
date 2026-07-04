package com.runningcrew.race.adapter.in.web.dto;

import com.runningcrew.race.application.view.CourseSummaryView;
import java.time.Instant;

/** 코스 목록 요소 응답(course-api.md §2). */
public record CourseSummaryResponse(
        long id,
        long crewId,
        String name,
        int distanceM,
        Instant createdAt) {

    public static CourseSummaryResponse from(CourseSummaryView v) {
        return new CourseSummaryResponse(v.id(), v.crewId(), v.name(), v.distanceM(), v.createdAt());
    }
}
