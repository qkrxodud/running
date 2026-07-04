package com.runningcrew.user.adapter.out.kakao;

import com.runningcrew.user.application.port.out.KakaoTokenInvalidException;
import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import com.runningcrew.user.domain.KakaoAccount;

/**
 * 카카오 토큰 검증 스텁(계약 auth-api.md §4) — <b>local/dev/sandbox 프로필 전용</b>.
 *
 * <p>{@code stub:{fake_kakao_id}} 형식만 수용 → 해당 KakaoAccount 반환(같은 fake id = 같은 User).
 * 그 외 전부 검증 실패.
 *
 * <p><b>배선</b>: 더 이상 독립 {@code @Component}가 아니다. {@link KakaoVerifierConfig}가 local/dev/sandbox에서
 * {@link DelegatingKakaoTokenVerifier} 내부에 이 스텁을 감싸 넣는다({@code stub:} 접두 → 스텁, 그 외 → 실 kapi).
 * prod에는 Real만 주입되어 스텁이 존재하지 않는다 — 스텁의 운영 유출은 프로필 배선으로 구조 차단.
 */
public class StubKakaoTokenVerifier implements KakaoTokenVerifier {

    private static final String PREFIX = "stub:";

    @Override
    public KakaoAccount verify(String kakaoAccessToken) {
        if (kakaoAccessToken == null || !kakaoAccessToken.startsWith(PREFIX)) {
            throw new KakaoTokenInvalidException("스텁 모드는 stub:{fake_kakao_id} 형식만 수용합니다.");
        }
        String fakeId = kakaoAccessToken.substring(PREFIX.length()).trim();
        if (fakeId.isEmpty()) {
            throw new KakaoTokenInvalidException("fake_kakao_id가 비어 있습니다.");
        }
        return new KakaoAccount(fakeId);
    }
}
