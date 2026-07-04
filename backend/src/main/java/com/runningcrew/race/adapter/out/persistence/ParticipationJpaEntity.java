package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.domain.ParticipationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code participation} 테이블 매핑(설계 §2.8). UQ(session_id,user_id). enum STRING 고정. */
@Entity
@Table(name = "participation")
public class ParticipationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ParticipationStatus status;

    protected ParticipationJpaEntity() {
    }

    public ParticipationJpaEntity(Long id, Long sessionId, Long userId, ParticipationStatus status) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.status = status;
    }

    public void updateStatus(ParticipationStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public ParticipationStatus getStatus() {
        return status;
    }
}
