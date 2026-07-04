package com.runningcrew.race.adapter.in.web.dto;

import com.runningcrew.race.application.view.SessionSummaryView;
import com.runningcrew.race.domain.RaceStatus;
import java.time.Instant;

/** 세션 목록 요소 응답(session-api.md §2). */
public record SessionSummaryResponse(
        long id,
        long crewId,
        long courseId,
        String courseName,
        RaceStatus status,
        Instant scheduledAt,
        Instant uploadDeadline,
        long participantCount) {

    public static SessionSummaryResponse from(SessionSummaryView v) {
        return new SessionSummaryResponse(v.id(), v.crewId(), v.courseId(), v.courseName(),
                v.status(), v.scheduledAt(), v.uploadDeadline(), v.participantCount());
    }
}
