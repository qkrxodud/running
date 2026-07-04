package com.runningcrew.common.web;

/**
 * 인증 필터와 인자 리졸버가 공유하는 요청 속성 키.
 *
 * <p>인증 필터(user 컨텍스트)가 검증 성공 시 내부 user id를 이 키로 요청 속성에 넣고,
 * {@link AuthenticatedUserArgumentResolver}(common)가 읽는다 — 컨텍스트 간 클래스 의존 없이
 * 문자열 계약으로 결합한다.
 */
public final class AuthAttributes {

    /** 인증된 내부 user id(Long)가 담기는 요청 속성 키. */
    public static final String AUTH_USER_ID = "com.runningcrew.auth.userId";

    private AuthAttributes() {
    }
}
