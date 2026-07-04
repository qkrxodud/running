package com.runningcrew.tracking.domain;

/**
 * 구간 페이스 요약(순수, A5). 리플레이 색상용(M3 소비) — 결과 API v0.1엔 미노출.
 *
 * @param index          0-base 구간 순번
 * @param startDistanceM 구간 시작 누적거리(m)
 * @param endDistanceM   구간 끝 누적거리(m) — 마지막 미완 구간은 총거리
 * @param durationS      구간 소요(초)
 * @param avgPaceSPerKm  구간 평균 페이스(초/km)
 */
public record TrackSegment(int index, int startDistanceM, int endDistanceM, long durationS,
                           int avgPaceSPerKm) {
}
