package com.runningcrew.crew.application.view;

import com.runningcrew.crew.domain.CrewStatus;
import java.time.Instant;
import java.util.List;

/**
 * 크루 상세 읽기 모델(계약 crew-api.md §3, CrewDetail). members는 ACTIVE만 joined_at 오름차순.
 */
public record CrewDetailView(
        Long id,
        String name,
        CrewStatus status,
        Long leaderUserId,
        String leaderNickname,
        Instant createdAt,
        List<CrewMemberView> members) {
}
