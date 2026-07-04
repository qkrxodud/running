package com.runningcrew.race.application.port.out;

import com.runningcrew.race.application.view.SessionDetailView;
import com.runningcrew.race.application.view.SessionSummaryView;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 세션 조회(읽기 모델) out-port. 참가자 nickname은 어댑터의 user 테이블 조인으로 해석(익명화 포함). */
public interface SessionQueryPort {

    Page<SessionSummaryView> findByCrew(Long crewId, Pageable pageable);

    Optional<SessionDetailView> findDetail(Long sessionId);
}
