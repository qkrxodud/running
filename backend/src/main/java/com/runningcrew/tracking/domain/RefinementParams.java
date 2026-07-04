package com.runningcrew.tracking.domain;

/**
 * 정제 파라미터(설계 42 §3.2) — <b>전부 외부화</b>(하드코딩 금지, FP-3 확장). 순수 함수에 주입한다.
 *
 * @param accuracyMaxM    accuracy 임계(초과 제거), 초기값 50
 * @param maxSpeedMps     이상 점프 임계(순간속도 초과 제거), 초기값 12(≈43km/h)
 * @param smoothingWindow 이동평균 창(홀수), 초기값 3
 * @param gapThresholdS   GPS 공백 임계(초), 초기값 30
 */
public record RefinementParams(double accuracyMaxM, double maxSpeedMps, int smoothingWindow,
                               int gapThresholdS) {

    /** 계획서 미규정 — 설계 42 §3.2 제안 초기값(골든 픽스처 축적 후 튜닝). */
    public static RefinementParams defaults() {
        return new RefinementParams(50.0, 12.0, 3, 30);
    }
}
