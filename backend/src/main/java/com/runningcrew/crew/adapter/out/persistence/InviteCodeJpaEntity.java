package com.runningcrew.crew.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code invite_code} 테이블 매핑(설계 §2.5). 자연키 code(PK).
 */
@Entity
@Table(name = "invite_code")
public class InviteCodeJpaEntity {

    @Id
    @Column(name = "code", length = 16)
    private String code;

    @Column(name = "crew_id", nullable = false)
    private Long crewId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    protected InviteCodeJpaEntity() {
    }

    public InviteCodeJpaEntity(String code, Long crewId, Instant expiresAt, int maxUses, int usedCount) {
        this.code = code;
        this.crewId = crewId;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
        this.usedCount = usedCount;
    }

    public void updateUsedCount(int usedCount) {
        this.usedCount = usedCount;
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
