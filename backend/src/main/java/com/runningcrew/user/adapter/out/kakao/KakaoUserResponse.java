package com.runningcrew.user.adapter.out.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 카카오 {@code GET /v2/user/me} 응답 중 서버가 필요로 하는 부분만 매핑.
 *
 * <p>서버는 카카오 회원번호({@code id})만 취해 {@link com.runningcrew.user.domain.KakaoAccount}로 봉인한다.
 * 닉네임·프로필 등 개인정보는 수집하지 않는다(온보딩 닉네임은 서버 placeholder — 설계 §1.2).
 * 카카오 응답 필드가 늘어도 무시(ignoreUnknown)한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(Long id) {
}
