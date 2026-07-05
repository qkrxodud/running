package com.runningcrew.replay.domain;

/**
 * 페이스→색상 버킷 경계(외부화, 설계 §3.3). 오름차순 경계 배열 — 페이스가 boundaries[i] 미만이면 버킷 i.
 * 가장 빠름(작은 페이스)=버킷 0, 경계를 넘을수록 버킷 증가(가장 느림=boundaries.length).
 *
 * <p>기본값(초/km): [240, 300, 360, 420] → 5버킷(≤4:00 / ≤5:00 / ≤6:00 / ≤7:00 / 초과). 러닝 페이스 스펙트럼.
 */
public record ColorParams(int[] boundariesSPerKm) {

    public static ColorParams defaults() {
        return new ColorParams(new int[] {240, 300, 360, 420});
    }

    /** 페이스(초/km) → 버킷 인덱스. 경계 미만이면 그 버킷, 전부 초과면 마지막 버킷. 결정적·순수. */
    public int bucketFor(int paceSPerKm) {
        for (int i = 0; i < boundariesSPerKm.length; i++) {
            if (paceSPerKm < boundariesSPerKm[i]) {
                return i;
            }
        }
        return boundariesSPerKm.length;
    }
}
