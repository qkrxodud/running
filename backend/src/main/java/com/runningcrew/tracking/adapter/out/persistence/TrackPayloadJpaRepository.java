package com.runningcrew.tracking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * track_payload 전용 리포지토리 — 저장 경로와 M3 리플레이·재정제 전용. 순위·결과·상태 조회 어댑터에
 * 주입 금지(블롭 격리 — TR-3).
 */
interface TrackPayloadJpaRepository extends JpaRepository<TrackPayloadJpaEntity, Long> {
}
