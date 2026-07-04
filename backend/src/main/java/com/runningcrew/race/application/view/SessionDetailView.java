package com.runningcrew.race.application.view;

import com.runningcrew.race.domain.ParticipationStatus;
import com.runningcrew.race.domain.RaceStatus;
import java.time.Instant;
import java.util.List;

/** 세션 상세 읽기 모델(session-api.md §3) — 코스 요약 + 참가자 상태 목록. */
public record SessionDetailView(
        Long id,
        Long crewId,
        RaceStatus status,
        Instant scheduledAt,
        Instant uploadDeadline,
        CourseSummary course,
        List<ParticipantView> participants) {

    /** 상세 내 코스 요약(폴리라인 포함 — 미리보기). */
    public record CourseSummary(
            Long id,
            String name,
            int distanceM,
            String routePolyline,
            double startLat,
            double startLng,
            double finishLat,
            double finishLng) {
    }

    /** 참가자 뷰. 탈퇴 유저는 nickname이 이미 "탈퇴한 러너"로 익명화되어 조인된다(RS-B7). */
    public record ParticipantView(
            Long userId,
            String nickname,
            ParticipationStatus status) {
    }
}
