package com.runningcrew.race.application;

/** 코스 생성 입력(어댑터 DTO → 유스케이스). distance_m는 받지 않는다 — 서버가 폴리라인에서 확정(CO-B3). */
public record CreateCourseCommand(
        String name,
        String routePolyline,
        double startLat,
        double startLng,
        double finishLat,
        double finishLng) {
}
