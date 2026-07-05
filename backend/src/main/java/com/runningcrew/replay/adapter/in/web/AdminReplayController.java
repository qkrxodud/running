package com.runningcrew.replay.adapter.in.web;

import com.runningcrew.replay.application.ReplayRegenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리플레이 재생성 admin API(replay-api §2) — 운영 토큰({@code X-Admin-Token}, AdminAuthInterceptor)으로만
 * 접근. 삭제→최신 스키마 재생성(멱등, RP-10). FAILED 관측·재시도 경로. FCM 재발송 안 함(RP-12).
 */
@RestController
@RequestMapping("/api/v1/admin/sessions/{sessionId}/replay")
public class AdminReplayController {

    private final ReplayRegenerationService regenerationService;

    public AdminReplayController(ReplayRegenerationService regenerationService) {
        this.regenerationService = regenerationService;
    }

    @PostMapping("/regenerate")
    public ResponseEntity<Void> regenerate(@PathVariable Long sessionId) {
        regenerationService.regenerate(sessionId);
        return ResponseEntity.accepted().build();
    }
}
