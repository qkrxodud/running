package com.runningcrew.tracking.domain;

/**
 * GPS 유실 구간 메타(리플레이 보간용, M3 소비). 인접 유효 포인트 간 Δt가 임계를 넘는 구간.
 *
 * @param startIndex 공백 시작(정제 포인트 인덱스)
 * @param endIndex   공백 끝(정제 포인트 인덱스)
 * @param deltaMillis 공백 지속(ms)
 */
public record GpsGap(int startIndex, int endIndex, long deltaMillis) {
}
