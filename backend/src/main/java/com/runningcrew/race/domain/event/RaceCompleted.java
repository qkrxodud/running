package com.runningcrew.race.domain.event;

import java.util.List;

/**
 * 레이스 물리 종료·참가자 최종화 완료 사실(과거형) — Ranking이 소비해 순위를 산정한다(A7).
 *
 * <p>Ranking은 이 이벤트만으로 자족하도록 참가자 최종 결과를 실어 보낸다(track/participation 재조회 없이).
 * 컨텍스트 간 결합은 domain.event로만(ArchUnit R-2) — 상태는 {@link FinalizedParticipant#status}
 * 문자열("FINISHED"/"DNF"/"DNS")로 전달해 타 컨텍스트 enum 참조를 피한다. {@code courseId}는 PB 조회용.
 */
public record RaceCompleted(Long sessionId, Long courseId, List<FinalizedParticipant> participants) {

    /**
     * @param userId         참가자
     * @param status         최종 상태 문자열(FINISHED/DNF/DNS)
     * @param recordTimeS    완주 기록(초, null 가능)
     * @param totalDistanceM 정제 거리(m, null 가능)
     */
    public record FinalizedParticipant(Long userId, String status, Integer recordTimeS,
                                       Integer totalDistanceM) {
    }
}
