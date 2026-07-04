package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.ParticipationRepository;
import com.runningcrew.race.domain.Participation;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link ParticipationRepository} 구현 — 도메인 ↔ JPA 엔티티 매핑. */
@Repository
public class ParticipationPersistenceAdapter implements ParticipationRepository {

    private final ParticipationJpaRepository jpa;

    public ParticipationPersistenceAdapter(ParticipationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Participation> findBySessionIdAndUserId(Long sessionId, Long userId) {
        return jpa.findBySessionIdAndUserId(sessionId, userId)
                .map(ParticipationPersistenceAdapter::toDomain);
    }

    @Override
    public Participation save(Participation participation) {
        ParticipationJpaEntity entity;
        if (participation.getId() == null) {
            entity = new ParticipationJpaEntity(null, participation.getSessionId(),
                    participation.getUserId(), participation.getStatus());
        } else {
            entity = jpa.findById(participation.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "참가 행이 존재하지 않습니다: " + participation.getId()));
            entity.updateStatus(participation.getStatus());
        }
        return toDomain(jpa.saveAndFlush(entity));
    }

    private static Participation toDomain(ParticipationJpaEntity e) {
        return new Participation(e.getId(), e.getSessionId(), e.getUserId(), e.getStatus());
    }
}
