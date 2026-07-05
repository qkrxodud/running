package com.runningcrew.replay.adapter.out.persistence;

import com.runningcrew.replay.application.port.out.ReplaySnapshotRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ReplaySnapshotRepository} 구현. 각 쓰기는 자체 트랜잭션(재생성이 삭제 커밋 후 REQUIRES_NEW 생성을
 * 순서대로 밟도록 — ReplayRegenerationService). 최신 조회는 created_at max(RP-10).
 */
@Repository
public class ReplaySnapshotPersistenceAdapter implements ReplaySnapshotRepository {

    private final ReplaySnapshotJpaRepository jpa;
    private final Clock clock;

    public ReplaySnapshotPersistenceAdapter(ReplaySnapshotJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Long saveGenerating(Long sessionId, int schemaVersion) {
        ReplaySnapshotJpaEntity entity = new ReplaySnapshotJpaEntity(
                sessionId, schemaVersion, "GENERATING", null, clock.instant());
        return jpa.save(entity).getId();
    }

    @Override
    @Transactional
    public void markReady(Long snapshotId, String payloadJson) {
        jpa.findById(snapshotId).ifPresent(e -> {
            e.markReady(payloadJson);
            jpa.save(e);
        });
    }

    @Override
    @Transactional
    public void markFailed(Long snapshotId) {
        jpa.findById(snapshotId).ifPresent(e -> {
            e.markFailed();
            jpa.save(e);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SnapshotRow> findLatestBySession(Long sessionId) {
        return jpa.findFirstBySessionIdOrderByCreatedAtDescIdDesc(sessionId)
                .map(e -> new SnapshotRow(e.getStatus(), e.getSchemaVersion(), e.getPayload()));
    }

    @Override
    @Transactional
    public void deleteBySession(Long sessionId) {
        jpa.deleteBySessionId(sessionId);
    }
}
