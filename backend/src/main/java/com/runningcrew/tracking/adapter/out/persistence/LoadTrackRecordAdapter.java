package com.runningcrew.tracking.adapter.out.persistence;

import com.runningcrew.tracking.application.port.out.LoadTrackRecordPort;
import com.runningcrew.tracking.application.view.TrackRecordSummary;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link LoadTrackRecordPort} 구현 — track_record만 조회(track_payload 조인 0건, TR-3).
 * payload 리포지토리를 주입받지 않는다(구조적 블롭 격리).
 */
@Repository
public class LoadTrackRecordAdapter implements LoadTrackRecordPort {

    private final TrackRecordJpaRepository recordJpa;

    public LoadTrackRecordAdapter(TrackRecordJpaRepository recordJpa) {
        this.recordJpa = recordJpa;
    }

    @Override
    public Optional<TrackRecordSummary> findBySessionIdAndUserId(Long sessionId, Long userId) {
        return recordJpa.findBySessionIdAndUserId(sessionId, userId).map(e ->
                new TrackRecordSummary(e.getId(), e.getSessionId(), e.getUserId(),
                        e.getClientUploadId(), e.getStartedAt(), e.getFinishedAt(),
                        e.getTotalDistanceM(), e.getTotalTimeS(), e.getGpsGapCount()));
    }
}
