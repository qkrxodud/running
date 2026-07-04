package com.runningcrew.race.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ParticipationJpaRepository extends JpaRepository<ParticipationJpaEntity, Long> {

    Optional<ParticipationJpaEntity> findBySessionIdAndUserId(Long sessionId, Long userId);
}
