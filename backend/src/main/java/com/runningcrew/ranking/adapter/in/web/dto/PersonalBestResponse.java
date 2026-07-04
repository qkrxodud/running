package com.runningcrew.ranking.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.runningcrew.ranking.application.view.PersonalBestView;
import java.time.Instant;

/** 코스별 개인 최고기록 응답(history-api §2). */
public record PersonalBestResponse(
        Long courseId,
        String courseName,
        int distanceM,
        int bestRecordTimeS,
        // 전역 SNAKE_CASE가 연속 대문자(S+Per)를 avg_pace_sper_km로 오변환 → 계약 필드명 고정.
        @JsonProperty("avg_pace_s_per_km") int avgPaceSPerKm,
        Long achievedSessionId,
        Instant achievedAt) {

    public static PersonalBestResponse from(PersonalBestView v) {
        return new PersonalBestResponse(v.courseId(), v.courseName(), v.distanceM(),
                v.bestRecordTimeS(), v.avgPaceSPerKm(), v.achievedSessionId(), v.achievedAt());
    }
}
