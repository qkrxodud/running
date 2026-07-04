package com.runningcrew.user.application.port.out;

/** access 토큰 부재·위조·typ 불일치 → 401 UNAUTHORIZED(클라: 재로그인). */
public class TokenInvalidException extends RuntimeException {
    public TokenInvalidException(String message) {
        super(message);
    }
}
