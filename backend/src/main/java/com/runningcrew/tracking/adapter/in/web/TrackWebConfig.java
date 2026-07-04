package com.runningcrew.tracking.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 트랙 업로드 경로에 본문 바이트 상한 인터셉터를 배선한다(TK-3 / R-006).
 *
 * <p>상한값은 설정 외부화({@code track.max-request-bytes}, 기본 8 MiB = 8,388,608). 경로 패턴은
 * 업로드 POST({@code /api/v1/sessions/{id}/track})만 대상 — 조회({@code .../track/me})는 단일
 * 세그먼트 {@code *} 패턴에 걸리지 않으며 preHandle도 POST만 검사한다.
 */
@Configuration
public class TrackWebConfig implements WebMvcConfigurer {

    private final long maxRequestBytes;

    public TrackWebConfig(@Value("${track.max-request-bytes:8388608}") long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TrackUploadSizeInterceptor(maxRequestBytes))
                .addPathPatterns("/api/v1/sessions/*/track");
    }
}
