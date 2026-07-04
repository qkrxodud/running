package com.runningcrew.common.error;

import org.springframework.http.HttpStatus;

/**
 * 공통 오류 코드(계약 conventions.md §4 초안 집합, 배치 A 범위).
 *
 * <p>각 코드는 HTTP 상태와 기본 메시지를 함께 가진다. 배치 B에서 도메인 코드가 확장된다.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    // 인증 401 세분(계약 auth-api.md §3 / conventions.md §4 v0.1.1). code로 클라 분기.
    AUTH_KAKAO_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "카카오 토큰 검증에 실패했습니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "자원을 찾을 수 없습니다."),
    // 초대 코드 오류(계약 crew-api.md §5): 없는 코드는 404, 만료·소진은 409.
    INVITE_CODE_INVALID(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."),
    INVITE_CODE_EXPIRED(HttpStatus.CONFLICT, "만료된 초대 코드입니다."),
    INVITE_CODE_EXHAUSTED(HttpStatus.CONFLICT, "사용 한도를 초과한 초대 코드입니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참가한 크루입니다."),
    CREW_CLOSED(HttpStatus.CONFLICT, "종료된 크루입니다."),
    COURSE_IMMUTABLE(HttpStatus.CONFLICT, "이미 발행된 코스는 수정할 수 없습니다."),
    SESSION_STATE_INVALID(HttpStatus.CONFLICT, "허용되지 않은 세션 상태 전이입니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
