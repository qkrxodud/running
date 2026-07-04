package com.runningcrew.user.application.port.out;

import com.runningcrew.user.domain.KakaoAccount;

/**
 * 카카오 액세스 토큰 → 카카오 회원번호 검증(설계 12 §2.1). 헥사고날 out-port(application 소유).
 *
 * <p>구현: local/dev는 {@code StubKakaoTokenVerifier}(스텁), prod는 실 카카오 어댑터(M0 키 대기).
 * prod/프로필 미지정에서는 이 포트 구현 빈이 없어 부팅 실패(fail-fast) — 스텁의 운영 유출을 구조로 차단.
 * 반환된 {@link KakaoAccount}는 user 조회/생성에만 쓰이고 응답·타 컨텍스트로 나가지 않는다.
 */
public interface KakaoTokenVerifier {

    KakaoAccount verify(String kakaoAccessToken);
}
