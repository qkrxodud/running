package com.runningcrew.ranking.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.ranking.application.port.out.LoadResultPort;
import com.runningcrew.ranking.application.view.ResultView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과·순위 조회(track-api §3). 크루 멤버만(403). 미확정 세션은 409 RESULT_NOT_READY(클라 결과 대기 화면 유지).
 */
@Service
public class ResultQueryService {

    private final LoadResultPort loadResultPort;

    public ResultQueryService(LoadResultPort loadResultPort) {
        this.loadResultPort = loadResultPort;
    }

    @Transactional(readOnly = true)
    public ResultView getResult(Long userId, Long sessionId) {
        if (!loadResultPort.sessionExists(sessionId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (!loadResultPort.isCrewMember(sessionId, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return loadResultPort.findResult(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESULT_NOT_READY));
    }
}
