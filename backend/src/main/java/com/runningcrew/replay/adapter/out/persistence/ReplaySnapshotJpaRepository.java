package com.runningcrew.replay.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplaySnapshotJpaRepository extends JpaRepository<ReplaySnapshotJpaEntity, Long> {

    /** 세션 최신 스냅샷(created_at max, 동시각은 id max 결정적). */
    Optional<ReplaySnapshotJpaEntity> findFirstBySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);

    @Modifying
    @Query("delete from ReplaySnapshotJpaEntity s where s.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
