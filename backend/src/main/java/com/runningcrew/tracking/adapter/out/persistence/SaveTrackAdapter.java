package com.runningcrew.tracking.adapter.out.persistence;

import com.runningcrew.tracking.application.port.out.SaveTrackPort;
import com.runningcrew.tracking.domain.TrackRecord;
import org.springframework.stereotype.Repository;

/**
 * {@link SaveTrackPort} 구현 — track_record(요약) + track_payload(블롭) 동시 저장. 쓰기 경로만 블롭을
 * 만진다. UQ(session_id,user_id) 위반(중복)은 상위 서비스의 멱등 검사로 선차단된다.
 */
@Repository
public class SaveTrackAdapter implements SaveTrackPort {

    private final TrackRecordJpaRepository recordJpa;
    private final TrackPayloadJpaRepository payloadJpa;

    public SaveTrackAdapter(TrackRecordJpaRepository recordJpa,
                            TrackPayloadJpaRepository payloadJpa) {
        this.recordJpa = recordJpa;
        this.payloadJpa = payloadJpa;
    }

    @Override
    public Long save(TrackRecord record, int gpsGapCount, String rawPayload, String refinedPayload) {
        TrackRecordJpaEntity entity = new TrackRecordJpaEntity(null, record.getSessionId(),
                record.getUserId(), record.getClientUploadId(), record.getStartedAt(),
                record.getFinishedAt(), record.getTotalDistanceM(), record.getTotalTimeS(),
                gpsGapCount);
        TrackRecordJpaEntity saved = recordJpa.saveAndFlush(entity);
        payloadJpa.saveAndFlush(
                new TrackPayloadJpaEntity(saved.getId(), rawPayload, refinedPayload));
        return saved.getId();
    }
}
