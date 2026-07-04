package com.runningcrew.race.adapter.in.web.dto;

import com.runningcrew.race.application.PromoteCourseCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /crews/{crewId}/courses/promote 요청(course-api.md §4). 소스는 본인 FINISHED track_record.
 * distance·좌표는 서버가 refined에서 확정하므로 받지 않는다(PR-4).
 */
public record PromoteCourseRequest(
        @NotNull Long sourceTrackRecordId,
        @NotBlank @Size(max = 50) String name) {

    public PromoteCourseCommand toCommand() {
        return new PromoteCourseCommand(sourceTrackRecordId, name);
    }
}
