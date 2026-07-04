package com.runningcrew.common.appversion;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 최소 버전 조회 유스케이스. 읽기 전용.
 */
@Service
public class AppVersionService {

    private final AppMinVersionRepository repository;

    public AppVersionService(AppMinVersionRepository repository) {
        this.repository = repository;
    }

    /**
     * 플랫폼별 최소 지원 버전 조회. 레코드가 없으면 404 NOT_FOUND(계약 app-version.md).
     */
    @Transactional(readOnly = true)
    public AppVersionResponse getMinVersion(Platform platform) {
        return repository.findById(platform)
                .map(AppVersionResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "해당 플랫폼의 최소 버전 정보가 없습니다."));
    }
}
