package com.runningcrew.common.appversion;

import java.time.Instant;

/**
 * GET /api/v1/app-version 응답 DTO(계약 app-version.md).
 *
 * <p>JSON 필드는 snake_case로 직렬화된다(전역 property-naming-strategy=SNAKE_CASE):
 * {@code platform, min_version, updated_at}. {@code updated_at}은 UTC ISO-8601(Z).
 */
public record AppVersionResponse(
        Platform platform,
        String minVersion,
        Instant updatedAt
) {
    public static AppVersionResponse from(AppMinVersion entity) {
        return new AppVersionResponse(
                entity.getPlatform(),
                entity.getMinVersion(),
                entity.getUpdatedAt());
    }
}
