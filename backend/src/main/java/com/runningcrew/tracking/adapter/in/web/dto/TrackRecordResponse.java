package com.runningcrew.tracking.adapter.in.web.dto;

import com.runningcrew.tracking.application.view.TrackRecordSummary;
import com.runningcrew.tracking.domain.FinishStatus;
import java.time.Instant;

/**
 * 트랙 업로드/상태 응답(track-api §1·§2). M2는 동기 처리라 processing_status는 항상 PROCESSED.
 * finish_status는 요약에서 파생(finished_at 유무). DNF면 finished_at·total_time_s null.
 */
public record TrackRecordResponse(
        Long trackRecordId,
        Long sessionId,
        Long userId,
        String processingStatus,
        FinishStatus finishStatus,
        Instant startedAt,
        Instant finishedAt,
        Integer totalDistanceM,
        Integer totalTimeS,
        int gpsGapCount) {

    public static TrackRecordResponse from(TrackRecordSummary s) {
        return new TrackRecordResponse(s.id(), s.sessionId(), s.userId(), "PROCESSED",
                s.finishStatus(), s.startedAt(), s.finishedAt(), s.totalDistanceM(),
                s.totalTimeS(), s.gpsGapCount());
    }
}
