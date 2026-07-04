package com.runningcrew.user.adapter.out.persistence;

import com.runningcrew.common.appversion.Platform;
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
 * {@code device_token} 테이블 매핑(설계 §2.2). fcm_token UNIQUE(upsert 기준).
 */
@Entity
@Table(name = "device_token")
public class DeviceTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fcm_token", nullable = false, length = 255)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private Platform platform;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceTokenJpaEntity() {
    }

    public DeviceTokenJpaEntity(Long userId, String fcmToken, Platform platform, Instant updatedAt) {
        this.userId = userId;
        this.fcmToken = fcmToken;
        this.platform = platform;
        this.updatedAt = updatedAt;
    }

    public void update(Long userId, Platform platform, Instant updatedAt) {
        this.userId = userId;
        this.platform = platform;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }
}
