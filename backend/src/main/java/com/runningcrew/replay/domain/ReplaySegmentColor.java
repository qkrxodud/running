package com.runningcrew.replay.domain;

/**
 * 구간 페이스 색상(스냅샷 스키마 v1 §1.4). TrackSegment(500m) 페이스 → 색상 버킷(순수 매핑).
 *
 * @param segIndex    0-base 구간 순번
 * @param startDistM  구간 시작 누적거리(m)
 * @param endDistM    구간 끝 누적거리(m)
 * @param paceSPerKm  구간 평균 페이스(초/km)
 * @param colorBucket 페이스→색상 버킷 인덱스(뷰어 색상표)
 */
public record ReplaySegmentColor(int segIndex, int startDistM, int endDistM, int paceSPerKm,
                                 int colorBucket) {
}
