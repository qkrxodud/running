package com.runningcrew.replay.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code replay_snapshot} 매핑(V1 §2.13). 복수 행 허용(재생성 멱등 — 최신=created_at max). payload는
 * READY에서만 non-null(GENERATING/FAILED는 NULL, RP-7). status는 STRING enum 규약(문자열 컬럼).
 */
@Entity
@Table(name = "replay_snapshot")
public class ReplaySnapshotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "payload")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReplaySnapshotJpaEntity() {
    }

    public ReplaySnapshotJpaEntity(Long sessionId, int schemaVersion, String status,
                                   String payload, Instant createdAt) {
        this.sessionId = sessionId;
        this.schemaVersion = schemaVersion;
        this.status = status;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public void markReady(String payloadJson) {
        this.status = "READY";
        this.payload = payloadJson;
    }

    public void markFailed() {
        this.status = "FAILED";
        this.payload = null;
    }
}
