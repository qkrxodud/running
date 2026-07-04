package com.runningcrew.race.domain;

/**
 * 세션 상태(설계 22 §2.2 · session-api.md). B2 구현 전이: 생성(→DRAFT)·open·start(→RUNNING)·cancel.
 * FINALIZING/COMPLETED는 M2(마감 스케줄러·결과 확정).
 */
public enum RaceStatus {
    DRAFT,
    OPEN,
    RUNNING,
    FINALIZING,
    COMPLETED,
    CANCELLED
}
