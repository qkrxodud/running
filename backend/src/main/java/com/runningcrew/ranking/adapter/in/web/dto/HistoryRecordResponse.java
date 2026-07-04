package com.runningcrew.ranking.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.runningcrew.ranking.application.view.HistoryRecordView;
import java.time.Instant;

/** 내 기록 히스토리 항목 응답(history-api §1). null 필드는 non_null 직렬화로 생략(DNF·CANCELLED). */
public record HistoryRecordResponse(
        Long trackRecordId,
        Long sessionId,
        Long courseId,
        String courseName,
        Instant scheduledAt,
        String finishStatus,
        Integer rank,
        Integer recordTimeS,
        Integer totalDistanceM,
        // 전역 SNAKE_CASE가 연속 대문자(S+Per)를 avg_pace_sper_km로 오변환 → 계약 필드명 고정.
        @JsonProperty("avg_pace_s_per_km") Integer avgPaceSPerKm,
        boolean isPb,
        boolean sessionCancelled) {

    public static HistoryRecordResponse from(HistoryRecordView v) {
        return new HistoryRecordResponse(v.trackRecordId(), v.sessionId(), v.courseId(),
                v.courseName(), v.scheduledAt(), v.finishStatus(), v.rank(), v.recordTimeS(),
                v.totalDistanceM(), v.avgPaceSPerKm(), v.isPb(), v.sessionCancelled());
    }
}
