package com.runningcrew.race.application.view;

import com.runningcrew.race.domain.RaceStatus;
import java.time.Instant;

/** 세션 목록 요소 읽기 모델(session-api.md §2). participantCount=참가 행 수(REGISTERED 이상). */
public record SessionSummaryView(
        Long id,
        Long crewId,
        Long courseId,
        String courseName,
        RaceStatus status,
        Instant scheduledAt,
        Instant uploadDeadline,
        long participantCount) {
}
