package com.runningcrew.common.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 전역 MVC 설정 — {@link AuthUserId} 인자 리졸버 + admin 경로 운영 토큰 게이트({@link AdminAuthInterceptor}).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String adminToken;

    public WebMvcConfig(@Value("${admin.token:}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedUserArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(adminToken))
                .addPathPatterns("/api/v1/admin/**");
    }
}
