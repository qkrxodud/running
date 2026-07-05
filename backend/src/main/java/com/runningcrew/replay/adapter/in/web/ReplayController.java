package com.runningcrew.replay.adapter.in.web;

import com.runningcrew.common.web.AuthUserId;
import com.runningcrew.replay.adapter.in.web.dto.ReplaySnapshotResponse;
import com.runningcrew.replay.application.ReplayQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 리플레이 스냅샷 조회 API(replay-api §1) — 인증 필요, 크루 멤버만(비멤버 403). */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/replay")
public class ReplayController {

    private final ReplayQueryService queryService;

    public ReplayController(ReplayQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ReplaySnapshotResponse getReplay(@AuthUserId Long userId, @PathVariable Long sessionId) {
        return ReplaySnapshotResponse.from(queryService.getReplay(userId, sessionId));
    }
}
