package com.runningcrew.race.adapter.in.web.dto;

import com.runningcrew.race.application.CreateCourseCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /crews/{crewId}/courses 요청(course-api.md §1). distance_m는 받지 않는다(서버 확정).
 * 좌표 범위 검증은 도메인({@code Course.create})에서 수행한다.
 */
public record CreateCourseRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank String routePolyline,
        @NotNull Double startLat,
        @NotNull Double startLng,
        @NotNull Double finishLat,
        @NotNull Double finishLng) {

    public CreateCourseCommand toCommand() {
        return new CreateCourseCommand(name, routePolyline, startLat, startLng, finishLat, finishLng);
    }
}
