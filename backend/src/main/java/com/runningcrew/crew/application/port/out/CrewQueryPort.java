package com.runningcrew.crew.application.port.out;

import com.runningcrew.crew.application.view.CrewDetailView;
import com.runningcrew.crew.application.view.CrewSummaryView;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 크루 조회(읽기 모델) out-port. 멤버 닉네임은 어댑터의 user 테이블 조인으로 해석(DTO 직반환) —
 * 명령 경로(이벤트/애그리거트)와 분리된 CQRS 성격의 read side.
 */
public interface CrewQueryPort {

    Optional<CrewDetailView> findDetail(Long crewId);

    boolean isActiveMember(Long crewId, Long userId);

    Page<CrewSummaryView> findMyCrews(Long userId, Pageable pageable);
}
