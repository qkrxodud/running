package com.runningcrew.race.adapter.in.web;

import com.runningcrew.common.web.AuthUserId;
import com.runningcrew.common.web.PageResponse;
import com.runningcrew.race.adapter.in.web.dto.CreateSessionRequest;
import com.runningcrew.race.adapter.in.web.dto.SessionDetailResponse;
import com.runningcrew.race.adapter.in.web.dto.SessionSummaryResponse;
import com.runningcrew.race.application.RaceSessionCommandService;
import com.runningcrew.race.application.RaceSessionQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 세션·참가 API(계약 session-api.md) — 전부 인증 필요. */
@RestController
@RequestMapping("/api/v1")
public class RaceSessionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final RaceSessionCommandService commandService;
    private final RaceSessionQueryService queryService;

    public RaceSessionController(RaceSessionCommandService commandService,
                                 RaceSessionQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/crews/{crewId}/sessions")
    public ResponseEntity<SessionDetailResponse> createSession(
            @AuthUserId Long userId,
            @PathVariable Long crewId,
            @Valid @RequestBody CreateSessionRequest request) {
        SessionDetailResponse body = SessionDetailResponse.from(
                commandService.createSession(userId, crewId, request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/crews/{crewId}/sessions")
    public PageResponse<SessionSummaryResponse> listSessions(
            @AuthUserId Long userId,
            @PathVariable Long crewId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int clampedPage = Math.max(page, 0);
        return PageResponse.from(queryService
                .listSessions(userId, crewId, PageRequest.of(clampedPage, clampedSize))
                .map(SessionSummaryResponse::from));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionDetailResponse getSession(@AuthUserId Long userId, @PathVariable Long sessionId) {
        return SessionDetailResponse.from(queryService.getSession(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/open")
    public SessionDetailResponse open(@AuthUserId Long userId, @PathVariable Long sessionId) {
        return SessionDetailResponse.from(commandService.openSession(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/register")
    public SessionDetailResponse register(@AuthUserId Long userId, @PathVariable Long sessionId) {
        return SessionDetailResponse.from(commandService.register(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/start")
    public SessionDetailResponse start(@AuthUserId Long userId, @PathVariable Long sessionId) {
        return SessionDetailResponse.from(commandService.start(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    public SessionDetailResponse cancel(@AuthUserId Long userId, @PathVariable Long sessionId) {
        return SessionDetailResponse.from(commandService.cancelSession(userId, sessionId));
    }
}
