package com.runningcrew.race.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 세션 마감 정책 — <b>순수 함수</b>(clock 주입 테스트, ArchUnit R-1, A9). 계획서 §8·설계 42 §6.
 *
 * <ul>
 *   <li><b>확정 트리거</b>(CL-1): STARTED 전원 업로드 <b>OR</b> {@code now ≥ uploadDeadline}.
 *   <li><b>참가자 최종화</b>(CL-2): STARTED+미업로드→DNF, STARTED+미완주→DNF, STARTED+완주→FINISHED,
 *       REGISTERED(미출주)→DNS. 이미 최종/WITHDRAWN은 보존.
 * </ul>
 *
 * <p>시각 판정은 주입된 {@code now}만 쓴다(내부 {@code Instant.now()} 금지 — 골든 재현성).
 */
public final class SessionClosePolicy {

    private SessionClosePolicy() {
    }

    /** 확정 시점 여부. deadline 도달이면 무조건, 아니면 STARTED 전원 업로드 시. */
    public static boolean shouldFinalize(List<ParticipantClose> participants, Instant now,
                                         Instant uploadDeadline) {
        if (!now.isBefore(uploadDeadline)) {
            return true;   // deadline 도달(now ≥ deadline)
        }
        boolean anyStarted = false;
        for (ParticipantClose p : participants) {
            if (p.current() == ParticipationStatus.STARTED) {
                anyStarted = true;
                if (!p.hasTrack()) {
                    return false;   // 아직 미업로드 STARTED 존재
                }
            }
        }
        return anyStarted;   // STARTED 전원 업로드(단, 최소 1명 STARTED)
    }

    /** 참가자별 최종 상태·기록 산출. WITHDRAWN은 제외한다(행 보존 — 순위 미포함). */
    public static List<ParticipantOutcome> finalize(List<ParticipantClose> participants) {
        List<ParticipantOutcome> outcomes = new ArrayList<>();
        for (ParticipantClose p : participants) {
            switch (p.current()) {
                case WITHDRAWN -> {
                    // 제외(행 보존)
                }
                case FINISHED -> outcomes.add(new ParticipantOutcome(p.userId(),
                        ParticipationStatus.FINISHED, p.recordTimeS(), p.totalDistanceM()));
                case DNF -> outcomes.add(new ParticipantOutcome(p.userId(),
                        ParticipationStatus.DNF, null, p.totalDistanceM()));
                case DNS -> outcomes.add(new ParticipantOutcome(p.userId(),
                        ParticipationStatus.DNS, null, null));
                case REGISTERED -> outcomes.add(new ParticipantOutcome(p.userId(),
                        ParticipationStatus.DNS, null, null));   // 미출주
                case STARTED -> {
                    if (!p.hasTrack()) {
                        outcomes.add(new ParticipantOutcome(p.userId(),
                                ParticipationStatus.DNF, null, null));   // 미업로드
                    } else if (p.trackFinished()) {
                        outcomes.add(new ParticipantOutcome(p.userId(),
                                ParticipationStatus.FINISHED, p.recordTimeS(), p.totalDistanceM()));
                    } else {
                        outcomes.add(new ParticipantOutcome(p.userId(),
                                ParticipationStatus.DNF, null, p.totalDistanceM()));   // 미완주
                    }
                }
                default -> throw new IllegalStateException("알 수 없는 상태: " + p.current());
            }
        }
        return outcomes;
    }
}
