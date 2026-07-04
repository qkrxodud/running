package com.runningcrew.ranking.application.view;

import java.time.Instant;

/**
 * 코스별 개인 최고기록 읽기 모델(history-api §2). 유저×course_id 최소 완주 record_time_s
 * (확정 세션 rank_entry의 FINISHED만 — RankingPolicy PB 정의와 동일 값, HS-3). CANCELLED·DNF 제외.
 */
public record PersonalBestView(
        Long courseId,
        String courseName,
        int distanceM,
        int bestRecordTimeS,
        int avgPaceSPerKm,
        Long achievedSessionId,
        Instant achievedAt) {
}
