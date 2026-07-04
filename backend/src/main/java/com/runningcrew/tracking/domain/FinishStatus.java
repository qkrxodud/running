package com.runningcrew.tracking.domain;

/**
 * 완주 판정 결과(FinishPolicy 산출). DNS(미출주)는 트랙 자체가 없어 업로드 경로 밖 — 여기 없음.
 */
public enum FinishStatus {
    FINISHED,
    DNF
}
