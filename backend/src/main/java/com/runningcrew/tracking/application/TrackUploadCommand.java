package com.runningcrew.tracking.application;

import java.time.Instant;

/**
 * 트랙 업로드 유스케이스 입력(web DTO와 분리 — 애플리케이션 계층 표현). {@code rawPayloadJson}은 원시 요청
 * 본문(무손실 보존용, track_payload.raw_payload). 병렬 배열은 원시 그대로(정제 전).
 */
public record TrackUploadCommand(String clientUploadId, Instant startedAt, String polyline,
                                 long[] timestamps, double[] speeds, double[] accuracies,
                                 double[] altitudes, String rawPayloadJson) {
}
