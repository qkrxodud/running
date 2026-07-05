package com.runningcrew.replay.application.port.out;

import java.util.Optional;

/**
 * 스냅샷 행 영속 out-port(A4·A6). GENERATING 저장 → READY(payload) / FAILED 전이, 최신 조회(created_at max),
 * 재생성 시 삭제. 복수 행 허용(재생성 멱등 — RP-10). payload는 READY에서만 non-null(RP-7).
 */
public interface ReplaySnapshotRepository {

    /** GENERATING 행 즉시 저장(payload NULL) → snapshotId. 관측 가능("생성 중"). */
    Long saveGenerating(Long sessionId, int schemaVersion);

    /** 계산 성공 → READY + payload 저장. */
    void markReady(Long snapshotId, String payloadJson);

    /** 계산 예외 → FAILED(payload NULL, 관측·재시도 대상, RP-8). */
    void markFailed(Long snapshotId);

    /** 세션 최신 스냅샷(created_at max). 없으면 empty. */
    Optional<SnapshotRow> findLatestBySession(Long sessionId);

    /** 재생성 전 기존 스냅샷 전삭제(삭제→최신 스키마 재생성 멱등). */
    void deleteBySession(Long sessionId);

    /** 최신 스냅샷 읽기 행(조회 API용). READY면 payloadJson non-null. */
    record SnapshotRow(String status, int schemaVersion, String payloadJson) {
    }
}
