package com.runningcrew.race.domain;

/**
 * 코스 생성 검증 위반(이름 길이·폴리라인 디코딩 실패·좌표 범위 등). 애플리케이션에서 400
 * {@code VALIDATION_ERROR}로 옮긴다. 순수 도메인(ArchUnit R-1).
 */
public class InvalidCourseException extends RuntimeException {

    public InvalidCourseException(String message) {
        super(message);
    }
}
