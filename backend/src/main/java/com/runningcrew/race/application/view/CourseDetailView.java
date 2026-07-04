package com.runningcrew.race.application.view;

import java.time.Instant;

/** 코스 상세 읽기 모델(course-api.md §3) — 폴리라인 포함(미리보기·완주 판정 M2 재사용). */
public record CourseDetailView(
        Long id,
        Long crewId,
        String name,
        String routePolyline,
        int distanceM,
        double startLat,
        double startLng,
        double finishLat,
        double finishLng,
        Long createdBy,
        Instant createdAt) {
}
