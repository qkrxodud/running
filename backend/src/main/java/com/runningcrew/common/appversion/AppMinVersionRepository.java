package com.runningcrew.common.appversion;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code app_min_version} 읽기 전용 조회. 자연키 {@link Platform} 기준.
 */
public interface AppMinVersionRepository extends JpaRepository<AppMinVersion, Platform> {
}
