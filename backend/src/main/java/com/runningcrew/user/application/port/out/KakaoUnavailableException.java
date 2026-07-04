package com.runningcrew.user.application.port.out;

/**
 * 카카오 kapi <b>서버 장애</b>(다운·타임아웃·5xx — 검증 자체 불가). 토큰 문제(401)와 분리 —
 * 어댑터 경계에서 {@code 503 AUTH_KAKAO_UNAVAILABLE}로 변환한다(계약 auth-api.md §1 v0.1.1).
 *
 * <p>클라 의미: 사용자 자격 문제가 아니므로 재로그인 유도 금지 — 잠시 후 재시도(무한 재로그인 루프 방지).
 */
public class KakaoUnavailableException extends RuntimeException {

    public KakaoUnavailableException(String message) {
        super(message);
    }
}
