package com.runningcrew.crew.domain;

/** 크루명 규칙(trim 후 1~50자) 위반 → 어댑터 경계에서 400 VALIDATION_ERROR. */
public class InvalidCrewNameException extends RuntimeException {
    public InvalidCrewNameException(String message) {
        super(message);
    }
}
