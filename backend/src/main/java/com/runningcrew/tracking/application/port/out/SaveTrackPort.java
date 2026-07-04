package com.runningcrew.tracking.application.port.out;

import com.runningcrew.tracking.domain.TrackRecord;

/**
 * 트랙 저장 out-port — track_record(요약) + track_payload(raw/refined 블롭) <b>동시 저장</b>.
 * 쓰기 경로만 블롭을 만진다(조회 포트는 track_record만 — TR-3).
 *
 * @return 생성된 track_record id
 */
public interface SaveTrackPort {

    Long save(TrackRecord record, int gpsGapCount, String rawPayload, String refinedPayload);
}
