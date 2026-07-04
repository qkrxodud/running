package com.runningcrew.crew.adapter.out.persistence;

import com.runningcrew.crew.domain.CrewMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

interface CrewMemberJpaRepository extends JpaRepository<CrewMemberJpaEntity, Long> {

    boolean existsByCrew_IdAndUserIdAndStatus(Long crewId, Long userId, CrewMemberStatus status);
}
