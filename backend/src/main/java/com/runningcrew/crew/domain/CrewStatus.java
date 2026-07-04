package com.runningcrew.crew.domain;

/** 크루 상태(계약 crew-api.md). CLOSED엔 모든 명령 409. */
public enum CrewStatus {
    ACTIVE,
    CLOSED
}
