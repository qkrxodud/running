package com.runningcrew.crew.application.port.out;

import com.runningcrew.crew.domain.Crew;
import java.util.List;
import java.util.Optional;

/**
 * Crew 애그리거트(멤버 포함) 영속 out-port. 어댑터가 도메인 ↔ JPA 엔티티 그래프를 매핑/reconcile한다.
 */
public interface CrewRepository {

    Optional<Crew> findById(Long id);

    /** 회원 탈퇴 정리용 — 해당 유저가 ACTIVE 멤버인 모든 크루(승계 계산 위해 멤버 전체 로드). */
    List<Crew> findAllByActiveMemberUserId(Long userId);

    Crew save(Crew crew);
}
