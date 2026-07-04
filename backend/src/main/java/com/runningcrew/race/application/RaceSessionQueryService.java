package com.runningcrew.race.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.race.application.port.out.CrewAccessPort;
import com.runningcrew.race.application.port.out.SessionQueryPort;
import com.runningcrew.race.application.view.SessionDetailView;
import com.runningcrew.race.application.view.SessionSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 조회 유스케이스(session-api.md §2·§3). ACTIVE 멤버만 조회(비멤버 403).
 */
@Service
public class RaceSessionQueryService {

    private final SessionQueryPort sessionQueryPort;
    private final CrewAccessPort crewAccessPort;

    public RaceSessionQueryService(SessionQueryPort sessionQueryPort, CrewAccessPort crewAccessPort) {
        this.sessionQueryPort = sessionQueryPort;
        this.crewAccessPort = crewAccessPort;
    }

    @Transactional(readOnly = true)
    public Page<SessionSummaryView> listSessions(Long userId, Long crewId, Pageable pageable) {
        if (!crewAccessPort.isActiveMember(crewId, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return sessionQueryPort.findByCrew(crewId, pageable);
    }

    @Transactional(readOnly = true)
    public SessionDetailView getSession(Long userId, Long sessionId) {
        SessionDetailView view = sessionQueryPort.findDetail(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!crewAccessPort.isActiveMember(view.crewId(), userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 비멤버 조회
        }
        return view;
    }
}
