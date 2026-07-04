package com.runningcrew.tracking.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code track_payload} 테이블 매핑(설계 §2.10, PK=FK). raw/refined 블롭(LONGTEXT). <b>별도 엔티티 +
 * 별도 리포지토리</b> — track_record와 @OneToOne으로 잇지 않는다(순위·상태 조회에 블롭 미동반, TR-3).
 * payload 접근은 저장 경로와 M3 리플레이·재정제 전용으로 한정한다.
 */
@Entity
@Table(name = "track_payload")
public class TrackPayloadJpaEntity {

    @Id
    @Column(name = "track_record_id")
    private Long trackRecordId;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "refined_payload")
    private String refinedPayload;

    protected TrackPayloadJpaEntity() {
    }

    public TrackPayloadJpaEntity(Long trackRecordId, String rawPayload, String refinedPayload) {
        this.trackRecordId = trackRecordId;
        this.rawPayload = rawPayload;
        this.refinedPayload = refinedPayload;
    }

    public Long getTrackRecordId() {
        return trackRecordId;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getRefinedPayload() {
        return refinedPayload;
    }
}
