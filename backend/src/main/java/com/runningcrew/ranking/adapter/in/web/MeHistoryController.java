package com.runningcrew.ranking.adapter.in.web;

import com.runningcrew.common.web.AuthUserId;
import com.runningcrew.common.web.PageResponse;
import com.runningcrew.ranking.adapter.in.web.dto.HistoryRecordResponse;
import com.runningcrew.ranking.adapter.in.web.dto.PersonalBestResponse;
import com.runningcrew.ranking.application.HistoryQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 기록 히스토리·PB API(history-api §1·§2) — 인증 필요, 본인 한정(토큰 sub). */
@RestController
@RequestMapping("/api/v1/me")
public class MeHistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final HistoryQueryService historyQueryService;

    public MeHistoryController(HistoryQueryService historyQueryService) {
        this.historyQueryService = historyQueryService;
    }

    @GetMapping("/records")
    public PageResponse<HistoryRecordResponse> myRecords(
            @AuthUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(historyQueryService
                .myRecords(userId, PageRequest.of(clampPage(page), clampSize(size)))
                .map(HistoryRecordResponse::from));
    }

    @GetMapping("/personal-bests")
    public PageResponse<PersonalBestResponse> myPersonalBests(
            @AuthUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(historyQueryService
                .myPersonalBests(userId, PageRequest.of(clampPage(page), clampSize(size)))
                .map(PersonalBestResponse::from));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static int clampPage(int page) {
        return Math.max(page, 0);
    }
}
