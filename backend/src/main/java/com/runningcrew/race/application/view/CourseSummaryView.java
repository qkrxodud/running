package com.runningcrew.race.application.view;

import java.time.Instant;

/** 코스 목록 요소 읽기 모델(course-api.md §2) — 폴리라인 미포함(경량). */
public record CourseSummaryView(
        Long id,
        Long crewId,
        String name,
        int distanceM,
        Instant createdAt) {
}
