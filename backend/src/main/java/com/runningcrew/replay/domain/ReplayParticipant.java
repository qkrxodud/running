package com.runningcrew.replay.domain;

import java.util.List;

/**
 * 리플레이 참가자(스냅샷 스키마 v1 §1.2). <b>user_id만</b> — 표시명 미내장(RP-3, 조회 시 조인).
 *
 * @param userId       참가자(표시명 미포함)
 * @param finishStatus {@code FINISHED}|{@code DNF}
 * @param finishTimeMs 완주 상대 시각(ms). DNF면 null(RP-6)
 * @param frames       t=0 상대 프레임열
 * @param segments     색상 구간
 */
public record ReplayParticipant(
        long userId,
        String finishStatus,
        Long finishTimeMs,
        List<ReplayFrame> frames,
        List<ReplaySegmentColor> segments) {
}
