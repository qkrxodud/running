package com.runningcrew.user.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.user.application.port.out.TokenProvider;
import com.runningcrew.user.application.port.out.UserRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * {@link JwtAuthenticationFilter}를 모든 요청(/*)에 등록. 화이트리스트는 필터의 shouldNotFilter가 판정.
 */
@Configuration
public class AuthFilterConfig {

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            TokenProvider tokenProvider, UserRepository userRepository, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(
                new JwtAuthenticationFilter(tokenProvider, userRepository, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
