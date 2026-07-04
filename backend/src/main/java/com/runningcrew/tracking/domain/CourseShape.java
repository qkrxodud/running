package com.runningcrew.tracking.domain;

import java.util.List;

/**
 * 완주 판정에 필요한 코스 최소 형상(tracking 로컬 표현 — race 컨텍스트 클래스 미참조, R-2).
 * 애플리케이션이 course 테이블(폴리라인·finish 좌표·distance_m)에서 조립해 순수 함수에 주입한다.
 *
 * @param polyline  코스 경로 좌표열(디코딩 결과)
 * @param finish    도착점 좌표
 * @param distanceM 코스 총거리(m) — 완주 판정 ② 기준
 */
public record CourseShape(List<TrackCoord> polyline, TrackCoord finish, int distanceM) {
}
