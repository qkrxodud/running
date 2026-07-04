package com.runningcrew.race.adapter.in.web.dto;

import com.runningcrew.race.application.view.SessionDetailView;
import com.runningcrew.race.domain.ParticipationStatus;
import com.runningcrew.race.domain.RaceStatus;
import java.time.Instant;
import java.util.List;

/** 세션 상세 응답(session-api.md §3) — 코스 요약 + 참가자 상태 목록. */
public record SessionDetailResponse(
        long id,
        long crewId,
        Course course,
        RaceStatus status,
        Instant scheduledAt,
        Instant uploadDeadline,
        List<Participant> participants) {

    public record Course(
            long id,
            String name,
            int distanceM,
            String routePolyline,
            double startLat,
            double startLng,
            double finishLat,
            double finishLng) {
    }

    public record Participant(long userId, String nickname, ParticipationStatus status) {
    }

    public static SessionDetailResponse from(SessionDetailView v) {
        SessionDetailView.CourseSummary c = v.course();
        Course course = new Course(c.id(), c.name(), c.distanceM(), c.routePolyline(),
                c.startLat(), c.startLng(), c.finishLat(), c.finishLng());
        List<Participant> participants = v.participants().stream()
                .map(p -> new Participant(p.userId(), p.nickname(), p.status()))
                .toList();
        return new SessionDetailResponse(v.id(), v.crewId(), course, v.status(),
                v.scheduledAt(), v.uploadDeadline(), participants);
    }
}
