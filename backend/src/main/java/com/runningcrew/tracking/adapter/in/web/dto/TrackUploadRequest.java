package com.runningcrew.tracking.adapter.in.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.tracking.application.TrackUploadCommand;
import com.runningcrew.tracking.application.TrackUploadService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * 트랙 업로드 요청(track-api §1). JSON snake_case(전역 Jackson). {@code client_meta}는 3키만 허용
 * (TK-4 — 서비스에서 재검증). 원시 본문은 무손실 보존(raw_payload)을 위해 재직렬화해 전달한다.
 */
public record TrackUploadRequest(
        @NotBlank String clientUploadId,
        @NotNull Instant startedAt,
        @NotBlank String polyline,
        @NotNull long[] timestamps,
        @NotNull double[] speeds,
        @NotNull double[] accuracies,
        double[] altitudes,
        Map<String, Object> clientMeta) {

    /** 검증(client_meta 키) 후 애플리케이션 커맨드로 변환. rawPayloadJson=요청 재직렬화(무손실 보존). */
    public TrackUploadCommand toCommand(ObjectMapper objectMapper) {
        TrackUploadService.validateClientMetaKeys(clientMeta);
        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("원시 페이로드 직렬화 실패", e);
        }
        return new TrackUploadCommand(clientUploadId, startedAt, polyline, timestamps, speeds,
                accuracies, altitudes, rawJson);
    }
}
