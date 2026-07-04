package com.runningcrew.user.application.port.out;

import com.runningcrew.common.appversion.Platform;
import java.time.Instant;

/**
 * 디바이스(FCM) 토큰 영속 out-port(계약 user-api.md §4).
 */
public interface DeviceTokenRepository {

    /** fcm_token 기준 upsert — 있으면 user_id·platform·updated_at 갱신, 없으면 신규 행. */
    void upsertByToken(String fcmToken, Long userId, Platform platform, Instant now);

    /** 탈퇴 시 해당 user의 디바이스 토큰 전부 파기(설계 §1.3 step4). */
    void deleteByUserId(Long userId);
}
