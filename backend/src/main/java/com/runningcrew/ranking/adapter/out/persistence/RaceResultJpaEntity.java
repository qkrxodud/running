package com.runningcrew.ranking.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code race_result} 테이블 매핑(설계 §2.11). 세션당 1결과(UQ(session_id)). */
@Entity
@Table(name = "race_result")
public class RaceResultJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "finalized_at", nullable = false)
    private Instant finalizedAt;

    protected RaceResultJpaEntity() {
    }

    public RaceResultJpaEntity(Long id, Long sessionId, Instant finalizedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.finalizedAt = finalizedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }
}
