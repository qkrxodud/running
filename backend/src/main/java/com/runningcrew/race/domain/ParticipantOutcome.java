package com.runningcrew.race.domain;

/**
 * 마감 확정 출력(참가자 1인) — 최종 participation 상태 + 기록. WITHDRAWN은 산출에서 제외(행 보존).
 *
 * @param userId         참가자
 * @param finalStatus    확정 상태(FINISHED/DNF/DNS)
 * @param recordTimeS    완주 기록(초, null 가능)
 * @param totalDistanceM 정제 거리(m, null 가능 — DNS는 트랙 없음)
 */
public record ParticipantOutcome(Long userId, ParticipationStatus finalStatus, Integer recordTimeS,
                                 Integer totalDistanceM) {
}
