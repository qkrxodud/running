package com.runningcrew.tracking.domain;

/**
 * 구간 페이스 파라미터(설계 42 §5.1) — 외부화. 초기값 500m.
 *
 * @param lengthM 구간 경계 누적거리(m)
 */
public record SegmentParams(int lengthM) {

    public static SegmentParams defaults() {
        return new SegmentParams(500);
    }
}
