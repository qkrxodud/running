package com.runningcrew.user.domain;

import java.util.Objects;

/**
 * 카카오 회원번호를 봉인하는 VO(설계 12 §1.1).
 *
 * <p><b>user 컨텍스트 밖(및 user.adapter.in.web)에서 참조 금지</b> — ArchUnit R-3가 강제한다.
 * 로그인 유스케이스와 영속 어댑터만 다루며, 어떤 응답·타 컨텍스트로도 나가지 않는다.
 */
public record KakaoAccount(String kakaoId) {

    public KakaoAccount {
        Objects.requireNonNull(kakaoId, "kakaoId");
        if (kakaoId.isBlank()) {
            throw new IllegalArgumentException("kakaoId는 비어 있을 수 없습니다.");
        }
    }
}
