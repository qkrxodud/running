package com.runningcrew.crew.application.view;

import com.runningcrew.crew.domain.CrewRole;
import java.time.Instant;

/**
 * 크루 상세의 멤버 요소 읽기 모델(계약 crew-api.md §3). nickname은 persistence의 user 테이블 조인 해석
 * (crew 컨텍스트는 user 도메인 클래스를 참조하지 않음 — ArchUnit R-2 정합).
 */
public record CrewMemberView(
        Long userId,
        String nickname,
        CrewRole role,
        Instant joinedAt) {
}
