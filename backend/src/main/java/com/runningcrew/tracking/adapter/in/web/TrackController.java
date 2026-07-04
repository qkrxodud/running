package com.runningcrew.tracking.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.common.web.AuthUserId;
import com.runningcrew.tracking.adapter.in.web.dto.TrackRecordResponse;
import com.runningcrew.tracking.adapter.in.web.dto.TrackUploadRequest;
import com.runningcrew.tracking.application.TrackQueryService;
import com.runningcrew.tracking.application.TrackUploadService;
import com.runningcrew.tracking.application.UploadOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 트랙 업로드·상태 조회 API(track-api §1·§2) — 전부 인증 필요, 참가자 본인. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/track")
public class TrackController {

    private final TrackUploadService uploadService;
    private final TrackQueryService queryService;
    private final ObjectMapper objectMapper;

    public TrackController(TrackUploadService uploadService, TrackQueryService queryService,
                           ObjectMapper objectMapper) {
        this.uploadService = uploadService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<TrackRecordResponse> upload(
            @AuthUserId Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody TrackUploadRequest request) {
        UploadOutcome outcome = uploadService.upload(userId, sessionId,
                request.toCommand(objectMapper));
        HttpStatus httpStatus = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(TrackRecordResponse.from(outcome.summary()));
    }

    @GetMapping("/me")
    public TrackRecordResponse mine(@AuthUserId Long userId, @PathVariable Long sessionId) {
        return TrackRecordResponse.from(queryService.findMine(userId, sessionId));
    }
}
