package com.runningcrew.crew.adapter.in.web.dto;

import com.runningcrew.crew.application.view.CrewDetailView;
import com.runningcrew.crew.domain.CrewRole;
import com.runningcrew.crew.domain.CrewStatus;
import java.time.Instant;
import java.util.List;

/** GET /api/v1/crews/{crewId} 및 생성/참가 응답의 CrewDetail(계약 crew-api.md §3). */
public record CrewDetailResponse(
        long id,
        String name,
        CrewStatus status,
        Leader leader,
        Instant createdAt,
        List<Member> members) {

    public record Leader(long userId, String nickname) {
    }

    public record Member(long userId, String nickname, CrewRole role, Instant joinedAt) {
    }

    public static CrewDetailResponse from(CrewDetailView v) {
        List<Member> members = v.members().stream()
                .map(m -> new Member(m.userId(), m.nickname(), m.role(), m.joinedAt()))
                .toList();
        return new CrewDetailResponse(v.id(), v.name(), v.status(),
                new Leader(v.leaderUserId(), v.leaderNickname()), v.createdAt(), members);
    }
}
