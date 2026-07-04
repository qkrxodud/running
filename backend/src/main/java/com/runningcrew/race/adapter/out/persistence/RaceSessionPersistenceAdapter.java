package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.RaceSessionRepository;
import com.runningcrew.race.domain.RaceSession;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link RaceSessionRepository} 구현 — 도메인 ↔ JPA 엔티티 매핑. 갱신은 status 플립만(현 범위). */
@Repository
public class RaceSessionPersistenceAdapter implements RaceSessionRepository {

    private final RaceSessionJpaRepository jpa;

    public RaceSessionPersistenceAdapter(RaceSessionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RaceSession save(RaceSession session) {
        RaceSessionJpaEntity entity;
        if (session.getId() == null) {
            entity = new RaceSessionJpaEntity(null, session.getCrewId(), session.getCourseId(),
                    session.getScheduledAt(), session.getUploadDeadline(), session.getStatus(),
                    session.getReplayNotifiedAt());
        } else {
            entity = jpa.findById(session.getId())
                    .orElseThrow(() -> new IllegalStateException("세션이 존재하지 않습니다: " + session.getId()));
            entity.updateStatus(session.getStatus());
        }
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<RaceSession> findById(Long id) {
        return jpa.findById(id).map(RaceSessionPersistenceAdapter::toDomain);
    }

    private static RaceSession toDomain(RaceSessionJpaEntity e) {
        return new RaceSession(e.getId(), e.getCrewId(), e.getCourseId(), e.getScheduledAt(),
                e.getUploadDeadline(), e.getStatus(), e.getReplayNotifiedAt());
    }
}
