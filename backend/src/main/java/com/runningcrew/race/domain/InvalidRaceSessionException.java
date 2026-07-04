package com.runningcrew.race.domain;

/**
 * 세션 생성 검증 위반(예: {@code upload_deadline <= scheduled_at}). 애플리케이션에서 400
 * {@code VALIDATION_ERROR}로 옮긴다. 순수 도메인(ArchUnit R-1).
 */
public class InvalidRaceSessionException extends RuntimeException {

    public InvalidRaceSessionException(String message) {
        super(message);
    }
}
