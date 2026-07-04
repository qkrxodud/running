package com.runningcrew.ranking.application.view;

import java.time.Instant;
import java.util.List;

/**
 * 결과·순위 조회 뷰(track-api §3). 블롭 미포함(track_record 요약·rank_entry·participation·user만 — TR-3).
 */
public record ResultView(Long sessionId, Long courseId, String courseName, int courseDistanceM,
                         Instant finalizedAt, List<EntryRow> entries) {

    /**
     * @param userId         참가자
     * @param nickname       닉네임(탈퇴 시 "탈퇴한 러너" 익명 — user.nickname 조인)
     * @param status         FINISHED/DNF/DNS
     * @param rank           순위(완주자만, null 가능)
     * @param recordTimeS    완주 기록(초, null 가능)
     * @param totalDistanceM 정제 거리(m, null 가능 — DNS)
     * @param isPb           PB 여부
     */
    public record EntryRow(Long userId, String nickname, String status, Integer rank,
                           Integer recordTimeS, Integer totalDistanceM, boolean isPb) {
    }
}
