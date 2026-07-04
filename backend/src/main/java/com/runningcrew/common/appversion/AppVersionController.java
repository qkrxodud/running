package com.runningcrew.common.appversion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/app-version — 강제 업데이트 판단용 최소 지원 버전 조회(계약 app-version.md).
 *
 * <p><b>인증 불요</b>(로그인보다 앞서 호출). {@code platform} 필수 쿼리 파라미터.
 * 누락/미지 값은 {@link com.runningcrew.common.error.GlobalExceptionHandler}가 400 VALIDATION_ERROR로,
 * 레코드 없음은 404 NOT_FOUND로 매핑한다.
 */
@RestController
@RequestMapping("/api/v1/app-version")
public class AppVersionController {

    private final AppVersionService service;

    public AppVersionController(AppVersionService service) {
        this.service = service;
    }

    @GetMapping
    public AppVersionResponse getAppVersion(@RequestParam("platform") Platform platform) {
        return service.getMinVersion(platform);
    }
}
