package com.runningcrew.tracking.domain;

import java.time.Instant;

/**
 * TrackRecord 애그리거트 루트(설계 42 §2.1) — 순위·PB·히스토리·상태 조회의 <b>유일한 진입점</b>.
 * 순수 도메인(ArchUnit R-1). 원시/정제 블롭은 별도 {@code TrackPayload}로 분리(조회에 블롭 미동반, TR-3).
 *
 * <p>DNF는 finished_at·total_time_s가 null(레이스 기록 미성립)이되 total_distance_m·경로는 보존한다.
 */
public class TrackRecord {

    private final Long id;
    private final Long sessionId;
    private final Long userId;
    private final String clientUploadId;
    private final Instant startedAt;
    private final Instant finishedAt;      // 도착 반경 최초 진입(FINISHED만). DNF null.
    private final Integer totalDistanceM;  // 정제 후 거리(FINISHED·DNF 모두 보존)
    private final Integer totalTimeS;      // 그로스 타임(FINISHED만). DNF null.
    private final FinishStatus finishStatus;

    public TrackRecord(Long id, Long sessionId, Long userId, String clientUploadId,
                       Instant startedAt, Instant finishedAt, Integer totalDistanceM,
                       Integer totalTimeS, FinishStatus finishStatus) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.clientUploadId = clientUploadId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.totalDistanceM = totalDistanceM;
        this.totalTimeS = totalTimeS;
        this.finishStatus = finishStatus;
    }

    /**
     * 판정 결과로부터 신규 레코드 조립. finished_at/total_time_s는 FINISHED일 때만 채운다.
     *
     * @param startedAt 시작 버튼 시각(요청 started_at)
     * @param refined   정제 결과(total_distance_m 원천)
     * @param judgment  완주/DNF 판정(finished_at 원천)
     */
    public static TrackRecord create(Long sessionId, Long userId, String clientUploadId,
                                     Instant startedAt, RefinedTrack refined,
                                     FinishJudgment judgment) {
        Instant finishedAt = null;
        Integer totalTimeS = null;
        if (judgment.finished() && judgment.finishedAtMillis() != null) {
            finishedAt = Instant.ofEpochMilli(judgment.finishedAtMillis());
            totalTimeS = (int) Math.max(0,
                    (finishedAt.toEpochMilli() - startedAt.toEpochMilli()) / 1000L);
        }
        return new TrackRecord(null, sessionId, userId, clientUploadId, startedAt, finishedAt,
                refined.totalDistanceM(), totalTimeS, judgment.status());
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getClientUploadId() {
        return clientUploadId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Integer getTotalDistanceM() {
        return totalDistanceM;
    }

    public Integer getTotalTimeS() {
        return totalTimeS;
    }

    public FinishStatus getFinishStatus() {
        return finishStatus;
    }
}
