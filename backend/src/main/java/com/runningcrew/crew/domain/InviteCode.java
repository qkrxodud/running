package com.runningcrew.crew.domain;

import java.time.Instant;

/**
 * 초대 코드 애그리거트(설계 12 §3.1). 만료·사용횟수 판정은 순수 함수(골든/유닛 테스트 대상).
 *
 * <p>불변식(C-B4): {@code used_count <= max_uses}, 만료는 UTC 판정. 가입은 코드로만(공개 경로 없음).
 */
public class InviteCode {

    private final String code;      // 대문자+숫자 6자, 혼동문자(0/O/1/I) 제외 — 생성기 소관
    private final Long crewId;
    private final Instant expiresAt;
    private final int maxUses;
    private int usedCount;

    public InviteCode(String code, Long crewId, Instant expiresAt, int maxUses, int usedCount) {
        this.code = code;
        this.crewId = crewId;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
        this.usedCount = usedCount;
    }

    /** 신규 생성 — usedCount 0. */
    public static InviteCode create(String code, Long crewId, Instant expiresAt, int maxUses) {
        return new InviteCode(code, crewId, expiresAt, maxUses, 0);
    }

    /**
     * 만료 판정(순수). 참가는 {@code expires_at > now}일 때만 유효 → {@code now >= expires_at}이면 만료.
     * 경계값(now == expires_at)은 만료로 본다.
     */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** 소진 판정(순수): {@code used_count >= max_uses}. */
    public boolean isExhausted() {
        return usedCount >= maxUses;
    }

    /** 사용 1회 반영(참가 성공 시). 호출 전 {@link #isExhausted()} 확인은 애플리케이션 소관. */
    public void incrementUse() {
        this.usedCount++;
    }

    public String getCode() {
        return code;
    }

    public Long getCrewId() {
        return crewId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }
}
