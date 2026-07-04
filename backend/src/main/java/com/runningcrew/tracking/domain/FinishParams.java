package com.runningcrew.tracking.domain;

/**
 * 완주 판정 임계값(설계 42 §4.1) — <b>전부 외부화</b>(하드코딩 금지, FP-3). 초기값은 계획서 명시값.
 * 경계는 <b>등호 포함</b>(설계 42 §4.3): 반경·코리도 {@code ≤}, 거리·일치율 {@code ≥}.
 *
 * @param radiusM         도착 반경(m), 초기값 30
 * @param minDistanceRatio 정제거리/코스거리 최소 비율, 초기값 0.90
 * @param coverageRatio   코스 일치율(코리도 내 포인트 비율), 초기값 0.80
 * @param corridorM       코스 폴리라인 코리도(m), 초기값 50
 */
public record FinishParams(double radiusM, double minDistanceRatio, double coverageRatio,
                           double corridorM) {

    public static FinishParams defaults() {
        return new FinishParams(30.0, 0.90, 0.80, 50.0);
    }
}
