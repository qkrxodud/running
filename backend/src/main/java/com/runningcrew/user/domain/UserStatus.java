package com.runningcrew.user.domain;

/**
 * User 상태(계약 user-api.md enum 집합). ORDINAL 금지 — 저장은 문자열.
 */
public enum UserStatus {
    ACTIVE,
    WITHDRAWN
}
