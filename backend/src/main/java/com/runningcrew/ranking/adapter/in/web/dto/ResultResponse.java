package com.runningcrew.ranking.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.runningcrew.ranking.application.view.ResultView;
import java.time.Instant;
import java.util.List;

/**
 * 결과·순위 응답(track-api §3). entries는 완주(rank↑) → DNF → DNS 순. avg_pace는 완주자만 파생.
 */
public record ResultResponse(
        Long sessionId,
        Course course,
        Instant finalizedAt,
        List<Entry> entries) {

    public record Course(Long id, String name, int distanceM) {
    }

    public record Entry(
            Long userId,
            String nickname,
            String status,
            Integer rank,
            Integer recordTimeS,
            Integer totalDistanceM,
            // 전역 SNAKE_CASE가 연속 대문자(S+Per)를 avg_pace_sper_km로 오변환하므로 계약 필드명을 고정한다.
            @JsonProperty("avg_pace_s_per_km") Integer avgPaceSPerKm,
            boolean isPb) {
    }

    public static ResultResponse from(ResultView v) {
        List<Entry> entries = v.entries().stream().map(e -> new Entry(
                e.userId(), e.nickname(), e.status(), e.rank(), e.recordTimeS(),
                e.totalDistanceM(), avgPace(e.recordTimeS(), e.totalDistanceM()), e.isPb()
        )).toList();
        return new ResultResponse(v.sessionId(),
                new Course(v.courseId(), v.courseName(), v.courseDistanceM()),
                v.finalizedAt(), entries);
    }

    /** 완주자만(기록·거리 모두 존재). 초/km = record_time_s / (distance/1000). */
    private static Integer avgPace(Integer recordTimeS, Integer totalDistanceM) {
        if (recordTimeS == null || totalDistanceM == null || totalDistanceM <= 0) {
            return null;
        }
        return (int) Math.round(recordTimeS / (totalDistanceM / 1000.0));
    }
}
