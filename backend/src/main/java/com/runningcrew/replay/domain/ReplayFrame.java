package com.runningcrew.replay.domain;

/**
 * 리플레이 프레임(스냅샷 스키마 v1 §1.2). 각자 시작 t=0 상대 시각·refined 좌표·누적 진행거리·GPS 유실 표기.
 *
 * @param tMs      시작 대비 상대 경과(ms)
 * @param lat      refined 위도
 * @param lng      refined 경도
 * @param cumDistM 누적 진행거리(m — 추월 계산 기준)
 * @param isGap    GPS 유실 보간 구간(뷰어 점선 표시)
 */
public record ReplayFrame(long tMs, double lat, double lng, int cumDistM, boolean isGap) {
}
