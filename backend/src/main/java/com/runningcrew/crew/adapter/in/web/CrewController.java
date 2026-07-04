package com.runningcrew.crew.adapter.in.web;

import com.runningcrew.common.web.AuthUserId;
import com.runningcrew.common.web.PageResponse;
import com.runningcrew.crew.adapter.in.web.dto.CreateCrewRequest;
import com.runningcrew.crew.adapter.in.web.dto.CrewDetailResponse;
import com.runningcrew.crew.adapter.in.web.dto.CrewSummaryResponse;
import com.runningcrew.crew.adapter.in.web.dto.InviteCodeCreateRequest;
import com.runningcrew.crew.adapter.in.web.dto.InviteCodeResponse;
import com.runningcrew.crew.adapter.in.web.dto.JoinCrewRequest;
import com.runningcrew.crew.application.CrewCommandService;
import com.runningcrew.crew.application.CrewQueryService;
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

/**
 * 크루 API(계약 crew-api.md) — 전부 인증 필요.
 */
@RestController
@RequestMapping("/api/v1/crews")
public class CrewController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CrewCommandService commandService;
    private final CrewQueryService queryService;

    public CrewController(CrewCommandService commandService, CrewQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<CrewDetailResponse> createCrew(@AuthUserId Long userId,
                                                         @Valid @RequestBody CreateCrewRequest request) {
        CrewDetailResponse body = CrewDetailResponse.from(
                commandService.createCrew(userId, request.name()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public PageResponse<CrewSummaryResponse> listMyCrews(
            @AuthUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int clampedPage = Math.max(page, 0);
        return PageResponse.from(queryService
                .listMyCrews(userId, PageRequest.of(clampedPage, clampedSize))
                .map(CrewSummaryResponse::from));
    }

    @GetMapping("/{crewId}")
    public CrewDetailResponse getCrew(@AuthUserId Long userId, @PathVariable Long crewId) {
        return CrewDetailResponse.from(queryService.getCrewDetail(userId, crewId));
    }

    @PostMapping("/{crewId}/invite-codes")
    public ResponseEntity<InviteCodeResponse> createInviteCode(
            @AuthUserId Long userId,
            @PathVariable Long crewId,
            @Valid @RequestBody InviteCodeCreateRequest request) {
        InviteCodeResponse body = InviteCodeResponse.from(commandService.createInviteCode(
                userId, crewId, request.maxUses(), request.expiresInHours()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/join")
    public CrewDetailResponse join(@AuthUserId Long userId, @Valid @RequestBody JoinCrewRequest request) {
        return CrewDetailResponse.from(commandService.joinCrew(userId, request.code()));
    }
}
