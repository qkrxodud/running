package com.runningcrew.replay.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 조회 API 보조 out-port(A8·A9). 세션 존재·크루 멤버 권한·표시명 조인(RP-3)·확정 시각(24h 조회율 측정 기준).
 * <b>track_payload 미접근</b>(조회 경로 격리 유지 — 스냅샷 payload는 ReplaySnapshotRepository가 별도 로드).
 */
public interface ReplayQueryPort {

    boolean sessionExists(Long sessionId);

    boolean isCrewMember(Long sessionId, Long userId);

    /** 결과 확정 시각(race_result.finalized_at) — 조회 로깅 24h 창 기준점(A9). 미확정이면 empty. */
    Optional<Instant> findFinalizedAt(Long sessionId);

    /** user_id → 표시명. 탈퇴 유저는 {@code "탈퇴한 러너"}(조회 시점 조인 — 익명화 정합, RP-3). */
    Map<Long, String> displayNames(List<Long> userIds);
}
