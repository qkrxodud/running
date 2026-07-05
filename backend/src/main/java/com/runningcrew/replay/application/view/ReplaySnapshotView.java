package com.runningcrew.replay.application.view;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 리플레이 조회 뷰(replay-api §1). status별 응답 — READY만 payload·display_names·schema_version, 그 외 null.
 *
 * @param status        {@code GENERATING}|{@code READY}|{@code FAILED}
 * @param schemaVersion READY만(뷰어 게이트 조기 판정). 그 외 null
 * @param displayNames  READY만 — user_id → 표시명(탈퇴="탈퇴한 러너", RP-3). 그 외 null
 * @param payload       READY만 — 스냅샷 스키마 v1 JSON. 그 외 null
 */
public record ReplaySnapshotView(
        String status,
        Integer schemaVersion,
        Map<Long, String> displayNames,
        JsonNode payload) {

    public static ReplaySnapshotView notReady(String status) {
        return new ReplaySnapshotView(status, null, null, null);
    }
}
