package com.runningcrew.tracking.application.view;

import com.runningcrew.tracking.domain.FinishStatus;
import java.time.Instant;

/**
 * track_record 요약 뷰(블롭 미포함 — TR-3). 업로드 응답·상태 조회(track-api §1·§2)의 원천.
 * finish_status는 {@code finishedAt} 유무로 파생(FINISHED=finished_at 존재, else DNF).
 */
public record TrackRecordSummary(Long id, Long sessionId, Long userId, String clientUploadId,
                                 Instant startedAt, Instant finishedAt, Integer totalDistanceM,
                                 Integer totalTimeS, int gpsGapCount) {

    public FinishStatus finishStatus() {
        return finishedAt != null ? FinishStatus.FINISHED : FinishStatus.DNF;
    }
}
