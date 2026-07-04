package com.runningcrew.user.domain;

import java.time.Instant;

/**
 * User 애그리거트 루트(설계 12 §1). 순수 도메인 — Spring/JPA 의존 금지(ArchUnit R-1).
 *
 * <p>불변식: 닉네임 항상 non-null(1~30자), 탈퇴는 ACTIVE에서만, 탈퇴 시 식별정보 파기.
 */
public class User {

    private final Long id;                 // 영속 전 null
    private String nickname;
    private KakaoAccount kakaoAccount;     // 탈퇴 시 null(파기)
    private UserStatus status;
    private final Instant createdAt;
    private Instant withdrawnAt;
    private Instant onboardedAt;           // null = 온보딩 미완

    public User(Long id, String nickname, KakaoAccount kakaoAccount, UserStatus status,
                Instant createdAt, Instant withdrawnAt, Instant onboardedAt) {
        this.id = id;
        this.nickname = nickname;
        this.kakaoAccount = kakaoAccount;
        this.status = status;
        this.createdAt = createdAt;
        this.withdrawnAt = withdrawnAt;
        this.onboardedAt = onboardedAt;
    }

    /** 최초 로그인 시 신규 User 생성 — placeholder 닉네임, 온보딩 미완(onboardedAt=null). */
    public static User createNew(KakaoAccount kakaoAccount, String placeholderNickname, Instant now) {
        return new User(null, Nickname.normalize(placeholderNickname), kakaoAccount,
                UserStatus.ACTIVE, now, null, null);
    }

    /**
     * 닉네임 설정/수정(온보딩 겸용). 최초 성공 시 onboardedAt을 now로 기록한다(설계 §1.2).
     */
    public void changeNickname(String rawNickname, Instant now) {
        this.nickname = Nickname.normalize(rawNickname);
        if (this.onboardedAt == null) {
            this.onboardedAt = now;
        }
    }

    /**
     * 탈퇴 처리(설계 §1.3 — 도메인 파트). 닉네임 익명화 + kakao_id 파기 + 상태 전이.
     * device_token·track_payload 파기는 어댑터/이벤트 소비 소관.
     *
     * @throws IllegalStateException ACTIVE가 아닌 경우(멱등 처리는 애플리케이션 레이어)
     */
    public void withdraw(Instant now) {
        if (this.status != UserStatus.ACTIVE) {
            throw new IllegalStateException("이미 탈퇴한 사용자입니다.");
        }
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.nickname = "탈퇴한 러너";   // M-4 확정 고정 문자열
        this.kakaoAccount = null;         // 카카오 회원번호 즉시 파기
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public boolean isOnboardingCompleted() {
        return this.onboardedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public KakaoAccount getKakaoAccount() {
        return kakaoAccount;
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
