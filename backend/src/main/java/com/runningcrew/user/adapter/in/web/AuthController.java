package com.runningcrew.user.adapter.in.web;

import com.runningcrew.user.adapter.in.web.dto.LoginRequest;
import com.runningcrew.user.adapter.in.web.dto.LoginResponse;
import com.runningcrew.user.adapter.in.web.dto.RefreshRequest;
import com.runningcrew.user.adapter.in.web.dto.TokenResponse;
import com.runningcrew.user.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API(계약 auth-api.md) — <b>인증 불요</b>(필터 화이트리스트).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authService.login(request.kakaoAccessToken()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }
}
