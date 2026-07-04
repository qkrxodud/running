package com.runningcrew.tracking.application.port.out;

import com.runningcrew.tracking.application.view.TrackRecordSummary;
import java.util.Optional;

/**
 * track_record <b>요약</b> 조회 out-port — <b>블롭 미로드</b>(track_payload 조인 0건, TR-3).
 * 업로드 멱등 판별·상태 조회(track-api §2)가 사용한다. payload 접근은 별도 포트(M3 리플레이·재정제 전용).
 */
public interface LoadTrackRecordPort {

    Optional<TrackRecordSummary> findBySessionIdAndUserId(Long sessionId, Long userId);
}
