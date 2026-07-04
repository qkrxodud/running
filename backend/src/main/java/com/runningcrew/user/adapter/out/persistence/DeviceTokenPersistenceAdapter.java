package com.runningcrew.user.adapter.out.persistence;

import com.runningcrew.common.appversion.Platform;
import com.runningcrew.user.application.port.out.DeviceTokenRepository;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * {@link DeviceTokenRepository} 구현 — fcm_token 기준 upsert, 탈퇴 시 user 기준 파기.
 */
@Repository
public class DeviceTokenPersistenceAdapter implements DeviceTokenRepository {

    private final DeviceTokenJpaRepository jpa;

    public DeviceTokenPersistenceAdapter(DeviceTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void upsertByToken(String fcmToken, Long userId, Platform platform, Instant now) {
        jpa.findByFcmToken(fcmToken).ifPresentOrElse(
                existing -> existing.update(userId, platform, now),
                () -> jpa.save(new DeviceTokenJpaEntity(userId, fcmToken, platform, now)));
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpa.deleteByUserId(userId);
    }
}
