package com.runningcrew.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.user.application.port.out.KakaoTokenInvalidException;
import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import com.runningcrew.user.application.port.out.KakaoUnavailableException;
import com.runningcrew.user.application.port.out.TokenProvider;
import com.runningcrew.user.application.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * M2-C: kapi 장애 → {@code 503 AUTH_KAKAO_UNAVAILABLE}(재시도), 토큰 문제 → {@code 401
 * AUTH_KAKAO_TOKEN_INVALID}(재로그인) 분리 매핑(auth-api §1 v0.1.1). AuthService 예외→ErrorCode 경계 검증.
 */
class AuthServiceKakaoUnavailableTest {

    private final TokenProvider tokenProvider = mock(TokenProvider.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void kapi_장애_KakaoUnavailable는_503_AUTH_KAKAO_UNAVAILABLE() {
        KakaoTokenVerifier verifier = mock(KakaoTokenVerifier.class);
        when(verifier.verify("t")).thenThrow(new KakaoUnavailableException("kapi 503"));
        AuthService service = new AuthService(verifier, tokenProvider, userRepository, clock);

        assertThatThrownBy(() -> service.login("t"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.AUTH_KAKAO_UNAVAILABLE));
    }

    @Test
    void 토큰_문제_KakaoTokenInvalid는_401_AUTH_KAKAO_TOKEN_INVALID() {
        KakaoTokenVerifier verifier = mock(KakaoTokenVerifier.class);
        when(verifier.verify("t")).thenThrow(new KakaoTokenInvalidException("expired"));
        AuthService service = new AuthService(verifier, tokenProvider, userRepository, clock);

        assertThatThrownBy(() -> service.login("t"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.AUTH_KAKAO_TOKEN_INVALID));
    }
}
