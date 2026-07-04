package com.runningcrew.tracking.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface TrackRecordJpaRepository extends JpaRepository<TrackRecordJpaEntity, Long> {

    Optional<TrackRecordJpaEntity> findBySessionIdAndUserId(Long sessionId, Long userId);
}
