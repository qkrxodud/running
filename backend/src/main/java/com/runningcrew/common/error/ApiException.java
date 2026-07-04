package com.runningcrew.common.error;

/**
 * 도메인/애플리케이션 규칙 위반을 통일 오류 응답으로 옮기기 위한 예외.
 *
 * <p>{@link ErrorCode}가 HTTP 상태와 기본 메시지를 결정한다. 필요 시 메시지를 재정의한다.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
