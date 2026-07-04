package com.runningcrew.crew.adapter.in.web.dto;

import com.runningcrew.crew.application.view.CrewSummaryView;
import com.runningcrew.crew.domain.CrewRole;
import com.runningcrew.crew.domain.CrewStatus;
import java.time.Instant;

/** GET /api/v1/crews 목록 요소(계약 crew-api.md §2). */
public record CrewSummaryResponse(
        long id,
        String name,
        CrewStatus status,
        long memberCount,
        CrewRole role,
        Instant createdAt) {

    public static CrewSummaryResponse from(CrewSummaryView v) {
        return new CrewSummaryResponse(v.id(), v.name(), v.status(), v.memberCount(), v.role(),
                v.createdAt());
    }
}
