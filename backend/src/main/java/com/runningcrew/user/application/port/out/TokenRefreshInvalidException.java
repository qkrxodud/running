package com.runningcrew.user.application.port.out;

/** refresh 토큰 만료·위조·typ!=refresh → 401 AUTH_REFRESH_INVALID(클라: 재로그인). */
public class TokenRefreshInvalidException extends RuntimeException {
    public TokenRefreshInvalidException(String message) {
        super(message);
    }
}
