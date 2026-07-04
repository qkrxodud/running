package com.runningcrew.race.domain;

/**
 * 마감 확정 입력(참가자 1인) — {@link SessionClosePolicy}. 트랙 판정 결과를 평탄화한 값(FinishPolicy가
 * 이미 완주/DNF를 확정 — FP-4, 여기서 코스 재판정 없음).
 *
 * @param userId         참가자
 * @param current        현 participation 상태
 * @param hasTrack       track_record 존재(업로드 여부)
 * @param trackFinished  track_record.finished_at 존재(완주 판정 결과)
 * @param recordTimeS    완주 기록(초). 완주만 유효
 * @param totalDistanceM 정제 거리(m). 업로드 시 존재(DNF도 뛴 만큼)
 */
public record ParticipantClose(Long userId, ParticipationStatus current, boolean hasTrack,
                               boolean trackFinished, Integer recordTimeS, Integer totalDistanceM) {
}
