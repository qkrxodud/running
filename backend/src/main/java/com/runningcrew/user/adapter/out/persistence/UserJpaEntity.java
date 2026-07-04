package com.runningcrew.user.adapter.out.persistence;

import com.runningcrew.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code user} 테이블 매핑(설계 §2.1 + V2 onboarded_at). 도메인 {@code User}와 분리 — 어댑터에서 매핑.
 */
@Entity
@Table(name = "user")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "kakao_id", length = 64)
    private String kakaoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    protected UserJpaEntity() {
    }

    public UserJpaEntity(Long id, String nickname, String kakaoId, UserStatus status,
                         Instant createdAt, Instant withdrawnAt, Instant onboardedAt) {
        this.id = id;
        this.nickname = nickname;
        this.kakaoId = kakaoId;
        this.status = status;
        this.createdAt = createdAt;
        this.withdrawnAt = withdrawnAt;
        this.onboardedAt = onboardedAt;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getKakaoId() {
        return kakaoId;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public Instant getOnboardedAt() {
        return onboardedAt;
    }
}
