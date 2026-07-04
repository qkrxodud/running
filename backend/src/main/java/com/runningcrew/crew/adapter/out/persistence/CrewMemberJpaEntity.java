package com.runningcrew.crew.adapter.out.persistence;

import com.runningcrew.crew.domain.CrewMemberStatus;
import com.runningcrew.crew.domain.CrewRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code crew_member} 테이블 매핑(설계 §2.4). UQ(crew_id,user_id) — 재가입은 기존 행 복원.
 */
@Entity
@Table(name = "crew_member")
public class CrewMemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crew_id", nullable = false)
    private CrewJpaEntity crew;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private CrewRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CrewMemberStatus status;

    protected CrewMemberJpaEntity() {
    }

    public CrewMemberJpaEntity(Long id, Long userId, CrewRole role, Instant joinedAt,
                               CrewMemberStatus status) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.status = status;
    }

    void setCrew(CrewJpaEntity crew) {
        this.crew = crew;
    }

    public void update(CrewRole role, Instant joinedAt, CrewMemberStatus status) {
        this.role = role;
        this.joinedAt = joinedAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public CrewRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public CrewMemberStatus getStatus() {
        return status;
    }
}
