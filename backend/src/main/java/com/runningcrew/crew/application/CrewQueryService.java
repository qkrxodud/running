package com.runningcrew.crew.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.crew.application.port.out.CrewQueryPort;
import com.runningcrew.crew.application.view.CrewDetailView;
import com.runningcrew.crew.application.view.CrewSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 조회 유스케이스(계약 crew-api.md §2·§3): 내 크루 목록, 크루 상세(멤버 전용).
 */
@Service
public class CrewQueryService {

    private final CrewQueryPort crewQueryPort;

    public CrewQueryService(CrewQueryPort crewQueryPort) {
        this.crewQueryPort = crewQueryPort;
    }

    @Transactional(readOnly = true)
    public Page<CrewSummaryView> listMyCrews(Long userId, Pageable pageable) {
        return crewQueryPort.findMyCrews(userId, pageable);
    }

    /** 멤버 전용 상세. 비멤버 403, 없는 crewId 404. */
    @Transactional(readOnly = true)
    public CrewDetailView getCrewDetail(Long userId, Long crewId) {
        CrewDetailView detail = crewQueryPort.findDetail(crewId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!crewQueryPort.isActiveMember(crewId, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return detail;
    }
}
