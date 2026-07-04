package com.runningcrew.user.application.port.out;

/** access 토큰 만료 → 401 AUTH_TOKEN_EXPIRED(클라: refresh 1회 시도). */
public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String message) {
        super(message);
    }
}
