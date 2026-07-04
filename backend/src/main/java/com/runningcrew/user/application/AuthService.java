package com.runningcrew.user.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.user.application.port.out.IssuedTokens;
import com.runningcrew.user.application.port.out.KakaoTokenInvalidException;
import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import com.runningcrew.user.application.port.out.TokenProvider;
import com.runningcrew.user.application.port.out.TokenRefreshInvalidException;
import com.runningcrew.user.application.port.out.UserRepository;
import com.runningcrew.user.domain.KakaoAccount;
import com.runningcrew.user.domain.User;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 유스케이스(계약 auth-api.md §1·§2): 카카오 토큰 → 자체 JWT 발급, refresh 쌍 회전.
 *
 * <p>탈퇴(WITHDRAWN) 사용자는 refresh 갱신도 거부(401 AUTH_REFRESH_INVALID) — 무상태 무효화를
 * per-request status 조회와 동일 경로로 달성(설계 §2.3).
 */
@Service
public class AuthService {

    private final KakaoTokenVerifier kakaoTokenVerifier;
    private final TokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(KakaoTokenVerifier kakaoTokenVerifier, TokenProvider tokenProvider,
                       UserRepository userRepository, Clock clock) {
        this.kakaoTokenVerifier = kakaoTokenVerifier;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** 카카오 토큰 검증 → 기존/신규 User 확정 → 토큰 쌍 발급. */
    @Transactional
    public LoginResult login(String kakaoAccessToken) {
        KakaoAccount account;
        try {
            account = kakaoTokenVerifier.verify(kakaoAccessToken);
        } catch (KakaoTokenInvalidException e) {
            throw new ApiException(ErrorCode.AUTH_KAKAO_TOKEN_INVALID);
        }

        Instant now = clock.instant();
        boolean[] isNew = {false};
        User user = userRepository.findByKakaoId(account.kakaoId())
                .orElseGet(() -> {
                    isNew[0] = true;
                    return userRepository.save(
                            User.createNew(account, generatePlaceholderNickname(), now));
                });

        IssuedTokens tokens = tokenProvider.issue(user.getId());
        return new LoginResult(user, isNew[0], tokens);
    }

    /** refresh 검증(만료·위조·탈퇴 거부) → access+refresh 새 쌍 발급(회전). */
    @Transactional(readOnly = true)
    public IssuedTokens refresh(String refreshToken) {
        long userId;
        try {
            userId = tokenProvider.verifyRefresh(refreshToken);
        } catch (TokenRefreshInvalidException e) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        return tokenProvider.issue(userId);
    }

    /** placeholder 닉네임: "러너" + 4자리(계약은 non-null만 보장, 구현 자유). */
    private String generatePlaceholderNickname() {
        return "러너" + (1000 + random.nextInt(9000));
    }
}
