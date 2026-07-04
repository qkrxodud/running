package com.runningcrew.race.adapter.in.web.dto;

import com.runningcrew.race.application.view.CourseDetailView;
import java.time.Instant;

/** 코스 상세 응답(course-api.md §3). route_polyline은 1e5 인코딩. */
public record CourseDetailResponse(
        long id,
        long crewId,
        String name,
        String routePolyline,
        int distanceM,
        double startLat,
        double startLng,
        double finishLat,
        double finishLng,
        long createdBy,
        Instant createdAt) {

    public static CourseDetailResponse from(CourseDetailView v) {
        return new CourseDetailResponse(v.id(), v.crewId(), v.name(), v.routePolyline(),
                v.distanceM(), v.startLat(), v.startLng(), v.finishLat(), v.finishLng(),
                v.createdBy(), v.createdAt());
    }
}
