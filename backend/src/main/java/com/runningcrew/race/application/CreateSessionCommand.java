package com.runningcrew.race.application;

import java.time.Instant;

/** 세션 생성 입력(어댑터 DTO → 유스케이스). "예정+12h" 기본값은 앱레이어 소관. */
public record CreateSessionCommand(
        Long courseId,
        Instant scheduledAt,
        Instant uploadDeadline) {
}
