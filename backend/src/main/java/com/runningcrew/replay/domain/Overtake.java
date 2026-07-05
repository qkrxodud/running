package com.runningcrew.replay.domain;

/**
 * 추월 이벤트(스냅샷 스키마 v1 §1.3). 동일 진행거리 도달 시각 비교의 부호 반전 지점(사전계산).
 *
 * @param atDistM       추월 발생 진행거리(m)
 * @param passerUserId  추월한 사람
 * @param passedUserId  추월당한 사람
 * @param tMs           추월 완료 상대 시각(둘 다 통과한 시점 = max(T_A, T_B))
 */
public record Overtake(int atDistM, long passerUserId, long passedUserId, long tMs) {
}
