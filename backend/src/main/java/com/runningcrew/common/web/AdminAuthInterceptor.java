package com.runningcrew.common.web;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리(admin) 경로 최소 인증 게이트 — 운영 토큰(`X-Admin-Token`) 대조. 일반 사용자 JWT가 아닌 운영 토큰
 * 방식(설계 72 §9: "admin 규약만 확정, 실 인증은 운영 게이트"). {@code admin.token} 미설정이면 admin 비활성
 * (전량 403 — prod 노출 제한). 토큰 불일치·부재도 403. preHandle 예외는 GlobalExceptionHandler가 {code,message}로.
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final String configuredToken;

    public AdminAuthInterceptor(String configuredToken) {
        this.configuredToken = configuredToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "관리 기능이 비활성화되어 있습니다.");
        }
        String provided = request.getHeader(ADMIN_TOKEN_HEADER);
        if (provided == null || !configuredToken.equals(provided)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "관리 권한이 없습니다.");
        }
        return true;
    }
}
