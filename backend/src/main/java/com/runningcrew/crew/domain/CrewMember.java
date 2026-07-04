package com.runningcrew.crew.domain;

import java.time.Instant;

/**
 * 크루 멤버십(애그리거트 Crew 내부 엔티티). 순수 도메인(ArchUnit R-1).
 */
public class CrewMember {

    private final Long id;        // 영속 전 null(신규 멤버)
    private final Long userId;
    private CrewRole role;
    private Instant joinedAt;
    private CrewMemberStatus status;

    public CrewMember(Long id, Long userId, CrewRole role, Instant joinedAt, CrewMemberStatus status) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.status = status;
    }

    /** 신규 ACTIVE 멤버(MEMBER). */
    static CrewMember newMember(Long userId, Instant joinedAt) {
        return new CrewMember(null, userId, CrewRole.MEMBER, joinedAt, CrewMemberStatus.ACTIVE);
    }

    /** 신규 크루장 멤버(LEADER). */
    static CrewMember newLeader(Long userId, Instant joinedAt) {
        return new CrewMember(null, userId, CrewRole.LEADER, joinedAt, CrewMemberStatus.ACTIVE);
    }

    void withdraw() {
        this.status = CrewMemberStatus.WITHDRAWN;
    }

    /** WITHDRAWN → ACTIVE 복원(재가입): joined_at 재참가 시각으로 갱신, role MEMBER(설계 §3.2). */
    void restore(Instant now) {
        this.status = CrewMemberStatus.ACTIVE;
        this.joinedAt = now;
        this.role = CrewRole.MEMBER;
    }

    void promoteToLeader() {
        this.role = CrewRole.LEADER;
    }

    public boolean isActive() {
        return this.status == CrewMemberStatus.ACTIVE;
    }

    public boolean isLeader() {
        return this.role == CrewRole.LEADER;
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
