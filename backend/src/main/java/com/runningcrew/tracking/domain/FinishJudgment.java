package com.runningcrew.tracking.domain;

/**
 * FinishPolicy 산출(순수). finished_at은 <b>도착 반경 최초 진입 시각</b>(FP-2, 계획서 §4) —
 * FINISHED일 때만 세팅, DNF는 null(레이스 기록 미성립).
 *
 * @param status          FINISHED | DNF
 * @param finishedAtMillis 도착 반경 최초 진입 GPS 시각(epoch ms). DNF면 null.
 */
public record FinishJudgment(FinishStatus status, Long finishedAtMillis) {

    public boolean finished() {
        return status == FinishStatus.FINISHED;
    }
}
