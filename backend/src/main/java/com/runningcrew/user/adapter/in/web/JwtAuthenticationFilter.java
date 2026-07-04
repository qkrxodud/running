package com.runningcrew.user.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.common.error.ApiError;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.common.web.AuthAttributes;
import com.runningcrew.user.application.port.out.TokenExpiredException;
import com.runningcrew.user.application.port.out.TokenInvalidException;
import com.runningcrew.user.application.port.out.TokenProvider;
import com.runningcrew.user.application.port.out.UserRepository;
import com.runningcrew.user.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 자체 JWT 인증 필터(계약 auth-api.md §3). 보호 경로에서 Bearer 액세스 토큰 검증 +
 * <b>매 요청 user status 조회</b>(WITHDRAWN은 유효 서명이어도 401 — 무상태 무효화, 설계 §2.3).
 *
 * <p>화이트리스트(인증 불요): {@code /api/v1/app-version}, {@code /api/v1/auth/**}, {@code /actuator/**}.
 * 401 code 분기: 만료→{@code AUTH_TOKEN_EXPIRED}, 그 외(부재·위조·WITHDRAWN)→{@code UNAUTHORIZED}.
 * 성공 시 내부 user id를 요청 속성({@link AuthAttributes#AUTH_USER_ID})에 넣는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final TokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(TokenProvider tokenProvider, UserRepository userRepository,
                                   ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/api/v1/app-version")
                || uri.startsWith("/api/v1/auth/")
                || uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        String token = header.substring(BEARER.length()).trim();

        long userId;
        try {
            userId = tokenProvider.verifyAccess(token);
        } catch (TokenExpiredException e) {
            writeError(response, ErrorCode.AUTH_TOKEN_EXPIRED);
            return;
        } catch (TokenInvalidException e) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || !user.get().isActive()) {
            // 없는 user 또는 WITHDRAWN → 재로그인 유도
            writeError(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        request.setAttribute(AuthAttributes.AUTH_USER_ID, userId);
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ApiError(code.name(), code.defaultMessage()));
    }
}
