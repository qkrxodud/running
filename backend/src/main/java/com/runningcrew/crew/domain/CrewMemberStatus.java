package com.runningcrew.crew.domain;

/** 크루 멤버십 상태(계약 crew-api.md). WITHDRAWN 멤버는 명단 미노출, 재가입 시 기존 행 복원. */
public enum CrewMemberStatus {
    ACTIVE,
    WITHDRAWN
}
