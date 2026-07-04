package com.runningcrew.user.domain;

/**
 * 닉네임 규칙(trim 후 1~30자, 제어문자 금지) 위반. 어댑터 경계에서 400 VALIDATION_ERROR로 변환된다.
 */
public class InvalidNicknameException extends RuntimeException {

    public InvalidNicknameException(String message) {
        super(message);
    }
}
