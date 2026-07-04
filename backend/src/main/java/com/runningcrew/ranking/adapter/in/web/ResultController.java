package com.runningcrew.ranking.adapter.in.web;

import com.runningcrew.common.web.AuthUserId;
import com.runningcrew.ranking.adapter.in.web.dto.ResultResponse;
import com.runningcrew.ranking.application.ResultQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 결과·순위 조회 API(track-api §3) — 인증 필요, 크루 멤버. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class ResultController {

    private final ResultQueryService resultQueryService;

    public ResultController(ResultQueryService resultQueryService) {
        this.resultQueryService = resultQueryService;
    }

    @GetMapping("/result")
    public ResultResponse getResult(@AuthUserId Long userId, @PathVariable Long sessionId) {
        return ResultResponse.from(resultQueryService.getResult(userId, sessionId));
    }
}
