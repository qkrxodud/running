package com.runningcrew.crew.adapter.in.web.dto;

import com.runningcrew.crew.domain.InviteCode;
import java.time.Instant;

/** POST /api/v1/crews/{crewId}/invite-codes 응답(계약 crew-api.md §4). */
public record InviteCodeResponse(
        String code,
        long crewId,
        Instant expiresAt,
        int maxUses,
        int usedCount) {

    public static InviteCodeResponse from(InviteCode ic) {
        return new InviteCodeResponse(ic.getCode(), ic.getCrewId(), ic.getExpiresAt(),
                ic.getMaxUses(), ic.getUsedCount());
    }
}
