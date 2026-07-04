package com.runningcrew.race.domain;

/**
 * 불법 상태 전이(설계 22 §2.4 매트릭스 위반). 애플리케이션에서 409 {@code SESSION_STATE_INVALID}로 옮긴다.
 * 순수 도메인(ArchUnit R-1).
 */
public class IllegalSessionTransitionException extends RuntimeException {

    public IllegalSessionTransitionException(RaceStatus current, SessionCommand command) {
        super("허용되지 않은 전이: status=" + current + ", command=" + command);
    }
}
