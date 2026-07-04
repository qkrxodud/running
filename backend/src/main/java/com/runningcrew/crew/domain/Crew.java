package com.runningcrew.crew.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Crew 애그리거트 루트(설계 12 §3). 순수 도메인(ArchUnit R-1) — 멤버십·승계·CLOSED 불변식 보유.
 *
 * <p>불변식: ACTIVE 크루엔 role=LEADER인 ACTIVE 멤버 정확히 1명(=leaderId) / 가입은 코드로만 /
 * 크루장 탈퇴 시 최선임 승계 / 마지막 1인 탈퇴 시 CLOSED.
 */
public class Crew {

    public static final int NAME_MIN = 1;
    public static final int NAME_MAX = 50;

    private final Long id;
    private String name;
    private Long leaderId;
    private CrewStatus status;
    private final Instant createdAt;
    private final List<CrewMember> members;

    public Crew(Long id, String name, Long leaderId, CrewStatus status, Instant createdAt,
                List<CrewMember> members) {
        this.id = id;
        this.name = name;
        this.leaderId = leaderId;
        this.status = status;
        this.createdAt = createdAt;
        this.members = new ArrayList<>(members);
    }

    /** 크루 생성 — 생성자가 leader_id이자 LEADER 멤버(같은 트랜잭션). */
    public static Crew create(String rawName, Long leaderUserId, Instant now) {
        String name = normalizeName(rawName);
        List<CrewMember> members = new ArrayList<>();
        members.add(CrewMember.newLeader(leaderUserId, now));
        return new Crew(null, name, leaderUserId, CrewStatus.ACTIVE, now, members);
    }

    private static String normalizeName(String rawName) {
        if (rawName == null) {
            throw new InvalidCrewNameException("크루명은 필수입니다.");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() < NAME_MIN || trimmed.length() > NAME_MAX) {
            throw new InvalidCrewNameException("크루명은 1~50자여야 합니다.");
        }
        return trimmed;
    }

    /**
     * 초대 코드 참가(설계 §3.2). CLOSED면 거부, 이미 ACTIVE면 거부, WITHDRAWN이면 기존 행 복원,
     * 처음이면 신규 MEMBER 추가.
     *
     * @throws CrewClosedException CLOSED 크루
     * @throws AlreadyJoinedException 이미 ACTIVE 멤버
     */
    public void join(Long userId, Instant now) {
        if (this.status == CrewStatus.CLOSED) {
            throw new CrewClosedException();
        }
        Optional<CrewMember> existing = findMember(userId);
        if (existing.isPresent()) {
            CrewMember m = existing.get();
            if (m.isActive()) {
                throw new AlreadyJoinedException();
            }
            m.restore(now);   // WITHDRAWN 재가입 = 기존 행 복원(UQ(crew_id,user_id) 정합)
        } else {
            this.members.add(CrewMember.newMember(userId, now));
        }
    }

    /**
     * 회원 탈퇴에 따른 멤버십 정리·승계(설계 §3.3, UserWithdrawn 소비 경로).
     * 해당 유저의 ACTIVE 멤버십을 WITHDRAWN 처리하고, 탈퇴자가 크루장이면 승계 또는 CLOSED.
     */
    public void handleMemberWithdrawn(Long userId, Instant now) {
        Optional<CrewMember> target = findActiveMember(userId);
        if (target.isEmpty()) {
            return; // 이 크루엔 정리할 ACTIVE 멤버십 없음
        }
        CrewMember withdrawing = target.get();
        boolean wasLeader = withdrawing.isLeader();
        withdrawing.withdraw();

        if (!wasLeader) {
            return; // 크루장 아님 → 멤버십 정리만(크루장 살아있으므로 CLOSED 아님)
        }
        // 크루장 탈퇴 → 승계 or CLOSED
        List<CrewMember> remaining = activeMembers();
        Optional<CrewMember> successor = LeaderSuccessionPolicy.selectSuccessor(remaining);
        if (successor.isPresent()) {
            CrewMember s = successor.get();
            s.promoteToLeader();
            this.leaderId = s.getUserId();
        } else {
            this.status = CrewStatus.CLOSED;   // 마지막 1인 탈퇴 (leaderId는 이력으로 유지)
        }
    }

    private Optional<CrewMember> findMember(Long userId) {
        return members.stream().filter(m -> m.getUserId().equals(userId)).findFirst();
    }

    private Optional<CrewMember> findActiveMember(Long userId) {
        return members.stream()
                .filter(m -> m.getUserId().equals(userId) && m.isActive())
                .findFirst();
    }

    private List<CrewMember> activeMembers() {
        return members.stream().filter(CrewMember::isActive).toList();
    }

    public boolean isClosed() {
        return this.status == CrewStatus.CLOSED;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public CrewStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 읽기용 멤버 스냅샷(영속 어댑터의 reconcile 용). */
    public List<CrewMember> getMembers() {
        return List.copyOf(members);
    }
}
