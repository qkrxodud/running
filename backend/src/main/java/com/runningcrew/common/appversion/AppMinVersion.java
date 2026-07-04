package com.runningcrew.common.appversion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code app_min_version} 테이블 매핑(설계문서 §2.16). 플랫폼별 최소 지원 버전 운영 메타.
 *
 * <p>자연키 {@code platform}이 PK. enum은 {@code @Enumerated(STRING)}(ORDINAL 금지 규약).
 * 시각은 UTC {@code Instant}.
 */
@Entity
@Table(name = "app_min_version")
public class AppMinVersion {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private Platform platform;

    @Column(name = "min_version", nullable = false, length = 20)
    private String minVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppMinVersion() {
        // JPA
    }

    public AppMinVersion(Platform platform, String minVersion, Instant updatedAt) {
        this.platform = platform;
        this.minVersion = minVersion;
        this.updatedAt = updatedAt;
    }

    public Platform getPlatform() {
        return platform;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
