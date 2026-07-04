package com.runningcrew.user.adapter.out.kakao;

import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import com.runningcrew.user.domain.KakaoAccount;

/**
 * 스텁·실 카카오 검증기 공존 라우터 — <b>local/dev/sandbox 프로필 전용</b>(오케스트레이터 확정 배선).
 *
 * <p>토큰이 {@code stub:} 접두면 {@link StubKakaoTokenVerifier}(기존 스텁 규약, 계약 §4)로, 아니면
 * {@link RealKakaoTokenVerifier}(실 kapi 호출)로 위임한다. 덕분에 카카오 앱 키 확보 전에도 dev 스텁 로그인과
 * 실 카카오 토큰 검증을 한 서버에서 동시에 시험할 수 있다.
 *
 * <p>prod는 이 라우터를 쓰지 않는다 — Real만 주입되어 {@code stub:} 토큰도 kapi 401로 거부된다(스텁 유출 차단).
 */
public class DelegatingKakaoTokenVerifier implements KakaoTokenVerifier {

    private static final String STUB_PREFIX = "stub:";

    private final KakaoTokenVerifier stub;
    private final KakaoTokenVerifier real;

    public DelegatingKakaoTokenVerifier(KakaoTokenVerifier stub, KakaoTokenVerifier real) {
        this.stub = stub;
        this.real = real;
    }

    @Override
    public KakaoAccount verify(String kakaoAccessToken) {
        if (kakaoAccessToken != null && kakaoAccessToken.startsWith(STUB_PREFIX)) {
            return stub.verify(kakaoAccessToken);
        }
        return real.verify(kakaoAccessToken);
    }
}
