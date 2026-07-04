package com.runningcrew.tracking.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code track_record} 테이블 매핑(설계 §2.9 + V3 client_upload_id·gps_gap_count). 요약만 — 블롭은
 * {@link TrackPayloadJpaEntity}로 분리(연관 두지 않음 — 조회에 블롭 미동반, TR-3). UQ(session_id,user_id).
 */
@Entity
@Table(name = "track_record")
public class TrackRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_upload_id", length = 64)
    private String clientUploadId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_distance_m")
    private Integer totalDistanceM;

    @Column(name = "total_time_s")
    private Integer totalTimeS;

    @Column(name = "gps_gap_count", nullable = false)
    private int gpsGapCount;

    protected TrackRecordJpaEntity() {
    }

    public TrackRecordJpaEntity(Long id, Long sessionId, Long userId, String clientUploadId,
                                Instant startedAt, Instant finishedAt, Integer totalDistanceM,
                                Integer totalTimeS, int gpsGapCount) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.clientUploadId = clientUploadId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.totalDistanceM = totalDistanceM;
        this.totalTimeS = totalTimeS;
        this.gpsGapCount = gpsGapCount;
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

    public int getGpsGapCount() {
        return gpsGapCount;
    }
}
