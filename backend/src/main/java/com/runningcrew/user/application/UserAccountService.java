package com.runningcrew.user.application;

import com.runningcrew.common.appversion.Platform;
import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.user.application.port.out.DeviceTokenRepository;
import com.runningcrew.user.application.port.out.UserRepository;
import com.runningcrew.user.domain.InvalidNicknameException;
import com.runningcrew.user.domain.User;
import com.runningcrew.user.domain.event.UserWithdrawn;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User 계정 유스케이스(계약 user-api.md): 프로필 조회, 닉네임 설정/수정, 탈퇴, 디바이스 토큰 등록.
 *
 * <p>탈퇴는 단일 트랜잭션(설계 §1.3): 상태 전이·식별정보 파기 후 {@link UserWithdrawn} 동기 발행 →
 * crew(멤버십 정리·승계)·tracking(위치 원본 파기)이 같은 TX에서 소비.
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UserAccountService(UserRepository userRepository,
                              DeviceTokenRepository deviceTokenRepository,
                              ApplicationEventPublisher eventPublisher,
                              Clock clock) {
        this.userRepository = userRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User getMe(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    /** 닉네임 설정/수정(온보딩 겸용). 최초 성공 시 onboarded_at 기록. */
    @Transactional
    public User changeNickname(Long userId, String rawNickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        try {
            user.changeNickname(rawNickname, clock.instant());
        } catch (InvalidNicknameException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
        return userRepository.save(user);
    }

    /**
     * 회원 탈퇴(계약 user-api.md §3). ACTIVE가 아니면 멱등 처리(no-op) — 동시/중복 요청 방어.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (!user.isActive()) {
            return; // 이미 탈퇴 — 멱등(계약: 두 번째 요청도 204 또는 401)
        }
        Instant now = clock.instant();
        user.withdraw(now);              // 닉네임 익명화 + kakao_id 파기 + 상태 전이(도메인)
        userRepository.save(user);
        deviceTokenRepository.deleteByUserId(userId);  // step4: 디바이스 토큰 파기
        // step5(track_payload 파기)·crew 멤버십 정리·승계는 UserWithdrawn 동기 소비자가 같은 TX에서 수행
        eventPublisher.publishEvent(new UserWithdrawn(userId));
    }

    /** FCM 디바이스 토큰 등록/갱신 — fcm_token 기준 upsert(계약 user-api.md §4). */
    @Transactional
    public void registerDeviceToken(Long userId, String fcmToken, Platform platform) {
        deviceTokenRepository.upsertByToken(fcmToken, userId, platform, clock.instant());
    }
}
