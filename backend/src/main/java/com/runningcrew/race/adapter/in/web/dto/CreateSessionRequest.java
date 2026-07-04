package com.runningcrew.race.adapter.in.web.dto;

import com.runningcrew.race.application.CreateSessionCommand;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * POST /crews/{crewId}/sessions 요청(session-api.md §1). {@code upload_deadline > scheduled_at}
 * 도메인 검증은 {@code RaceSession.create}에서. "예정+12h" 기본값은 앱레이어 소관.
 */
public record CreateSessionRequest(
        @NotNull Long courseId,
        @NotNull Instant scheduledAt,
        @NotNull Instant uploadDeadline) {

    public CreateSessionCommand toCommand() {
        return new CreateSessionCommand(courseId, scheduledAt, uploadDeadline);
    }
}
