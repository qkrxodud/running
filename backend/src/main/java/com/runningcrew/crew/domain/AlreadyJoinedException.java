package com.runningcrew.crew.domain;

/** 이미 ACTIVE 멤버가 재참가 시도 → 어댑터 경계에서 409 ALREADY_JOINED. */
public class AlreadyJoinedException extends RuntimeException {
    public AlreadyJoinedException() {
        super("이미 참가한 크루입니다.");
    }
}
