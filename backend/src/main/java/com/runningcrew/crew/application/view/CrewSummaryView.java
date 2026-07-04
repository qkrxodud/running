package com.runningcrew.crew.application.view;

import com.runningcrew.crew.domain.CrewRole;
import com.runningcrew.crew.domain.CrewStatus;
import java.time.Instant;

/**
 * 내 크루 목록 요소 읽기 모델(계약 crew-api.md §2). member_count는 ACTIVE 멤버 수, role은 요청자 역할.
 */
public record CrewSummaryView(
        Long id,
        String name,
        CrewStatus status,
        long memberCount,
        CrewRole role,
        Instant createdAt) {
}
