package com.runningcrew.replay.domain;

import java.util.List;

/**
 * 병합 결과(A1 출력). 참가자별 t=0 상대 타임라인 + 전체 슬라이더 길이.
 *
 * @param participants 각자 t=0 정렬 프레임·색상 구간
 * @param durationMs   전 참가자 최대 상대 종료 시각(슬라이더 길이)
 */
public record MergedTimeline(List<ReplayParticipant> participants, long durationMs) {
}
