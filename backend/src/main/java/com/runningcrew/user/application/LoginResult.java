package com.runningcrew.user.application;

import com.runningcrew.user.application.port.out.IssuedTokens;
import com.runningcrew.user.domain.User;

/**
 * 로그인 유스케이스 결과 — 발급 토큰 + 신규 여부 + 사용자. web 어댑터가 계약 응답 DTO로 매핑한다.
 */
public record LoginResult(User user, boolean newUser, IssuedTokens tokens) {
}
