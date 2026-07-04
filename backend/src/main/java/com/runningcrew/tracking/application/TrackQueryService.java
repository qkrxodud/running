package com.runningcrew.tracking.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.tracking.application.port.out.LoadTrackRecordPort;
import com.runningcrew.tracking.application.port.out.TrackUploadSupportPort;
import com.runningcrew.tracking.application.view.TrackRecordSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 트랙 상태 조회(track-api §2). track_record 요약만 반환(블롭 격리 — TR-3). 미업로드면 404.
 */
@Service
public class TrackQueryService {

    private final TrackUploadSupportPort supportPort;
    private final LoadTrackRecordPort loadPort;

    public TrackQueryService(TrackUploadSupportPort supportPort, LoadTrackRecordPort loadPort) {
        this.supportPort = supportPort;
        this.loadPort = loadPort;
    }

    @Transactional(readOnly = true)
    public TrackRecordSummary findMine(Long userId, Long sessionId) {
        if (supportPort.findSessionStatus(sessionId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);   // 세션 없음
        }
        if (!supportPort.participationExists(sessionId, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 참가자 아님
        }
        return loadPort.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));   // 내 트랙 미업로드
    }
}
