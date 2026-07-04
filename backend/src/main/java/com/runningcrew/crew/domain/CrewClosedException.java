package com.runningcrew.crew.domain;

/** CLOSED 크루에 명령 시도 → 어댑터 경계에서 409 CREW_CLOSED. */
public class CrewClosedException extends RuntimeException {
    public CrewClosedException() {
        super("종료된 크루입니다.");
    }
}
