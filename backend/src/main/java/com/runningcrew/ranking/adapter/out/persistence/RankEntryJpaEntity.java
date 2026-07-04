package com.runningcrew.ranking.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code rank_entry} 테이블 매핑(설계 §2.12). <b>{@code rank}는 MySQL 8.0.2+ 예약어</b> — 전역 인용
 * ({@code globally_quoted_identifiers=true}, application.yml)으로 안전 매핑한다(RE-1, R-003 이월5 —
 * Testcontainers {@code ddl-auto=validate} 부팅 성공이 인용 정합의 증명). user FK RESTRICT(익명 보존, RE-2).
 */
@Entity
@Table(name = "rank_entry")
public class RankEntryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "result_id", nullable = false)
    private Long resultId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 예약어 컬럼 — 전역 인용으로 `rank`가 되어야 validate 통과(R-003).
    @Column(name = "rank")
    private Integer rank;

    @Column(name = "record_time_s")
    private Integer recordTimeS;

    @Column(name = "is_pb", nullable = false)
    private boolean pb;

    protected RankEntryJpaEntity() {
    }

    public RankEntryJpaEntity(Long id, Long resultId, Long userId, Integer rank, Integer recordTimeS,
                              boolean pb) {
        this.id = id;
        this.resultId = resultId;
        this.userId = userId;
        this.rank = rank;
        this.recordTimeS = recordTimeS;
        this.pb = pb;
    }

    public Long getId() {
        return id;
    }

    public Long getResultId() {
        return resultId;
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getRank() {
        return rank;
    }

    public Integer getRecordTimeS() {
        return recordTimeS;
    }

    public boolean isPb() {
        return pb;
    }
}
