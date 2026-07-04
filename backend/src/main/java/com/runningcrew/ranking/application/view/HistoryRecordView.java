package com.runningcrew.ranking.application.view;

import java.time.Instant;

/**
 * 내 기록 히스토리 항목 읽기 모델(history-api §1). track_record 스캔 + race_session/rank_entry 조인
 * (<b>track_payload 조인 0건</b> — HS-2). CANCELLED 세션 트랙은 배지 노출·rank/is_pb 없음(CX-2).
 *
 * @param rank           확정 세션 완주자만. DNF·CANCELLED null
 * @param recordTimeS    완주 그로스 타임. DNF null
 * @param avgPaceSPerKm  완주자만
 * @param isPb           완주만 PB 후보. DNF·CANCELLED false
 * @param sessionCancelled true면 "취소된 세션" 배지(개인 기록 보존)
 */
public record HistoryRecordView(
        Long trackRecordId,
        Long sessionId,
        Long courseId,
        String courseName,
        Instant scheduledAt,
        String finishStatus,
        Integer rank,
        Integer recordTimeS,
        Integer totalDistanceM,
        Integer avgPaceSPerKm,
        boolean isPb,
        boolean sessionCancelled) {
}
