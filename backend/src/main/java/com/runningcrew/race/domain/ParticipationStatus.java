package com.runningcrew.race.domain;

/**
 * 참가 상태(서버 — 설계 22 §3.1). B2 능동 전이: REGISTERED(register)·STARTED(start).
 * WITHDRAWN은 유저 탈퇴 시 행 보존 표시. FINISHED/DNF/DNS는 M2(업로드·FinishPolicy·마감 판정).
 *
 * <p>클라 로컬 상태머신(READY/RUNNING/…)과 별개다 — 서버 상태에 클라 상태를 넣지 않는다.
 */
public enum ParticipationStatus {
    REGISTERED,
    STARTED,
    FINISHED,
    DNF,
    DNS,
    WITHDRAWN
}
