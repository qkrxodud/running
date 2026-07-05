package com.runningcrew.replay.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.runningcrew.replay.application.view.ReplaySnapshotView;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 리플레이 조회 응답(replay-api §1). status별 — READY만 schema_version·display_names·payload, 그 외 null.
 * display_names 키는 문자열 user_id(계약: {@code {"3":"민수"}}).
 *
 * <p>{@code @JsonInclude(ALWAYS)} — 전역 non_null을 오버라이드해 GENERATING/FAILED 응답의 3필드를
 * <b>명시적 null</b>로 직렬화한다(계약 §1 리터럴: {@code {"status":"GENERATING","schema_version":null,…}}).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReplaySnapshotResponse(
        String status,
        Integer schemaVersion,
        Map<String, String> displayNames,
        JsonNode payload) {

    public static ReplaySnapshotResponse from(ReplaySnapshotView v) {
        Map<String, String> names = null;
        if (v.displayNames() != null) {
            names = new LinkedHashMap<>();
            for (Map.Entry<Long, String> e : v.displayNames().entrySet()) {
                names.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return new ReplaySnapshotResponse(v.status(), v.schemaVersion(), names, v.payload());
    }
}
