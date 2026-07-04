package com.runningcrew.user.application.port.out;

/**
 * 카카오 토큰 검증 실패(만료·위조·스텁 형식 불일치). 어댑터 경계에서 401 AUTH_KAKAO_TOKEN_INVALID로 변환.
 */
public class KakaoTokenInvalidException extends RuntimeException {

    public KakaoTokenInvalidException(String message) {
        super(message);
    }
}
