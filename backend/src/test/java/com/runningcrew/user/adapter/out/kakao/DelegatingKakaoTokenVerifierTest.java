package com.runningcrew.user.adapter.out.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runningcrew.user.application.port.out.KakaoTokenInvalidException;
import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import com.runningcrew.user.domain.KakaoAccount;
import org.junit.jupiter.api.Test;

/**
 * DelegatingKakaoTokenVerifier 분기 테스트 — {@code stub:} 접두는 스텁으로, 그 외는 실 검증기로 라우팅.
 *
 * <p>실 검증기는 kapi 호출 대신 라우팅만 확인하는 페이크로 대체(실 kapi 통합은 RealKakaoTokenVerifierTest).
 */
class DelegatingKakaoTokenVerifierTest {

    /** verify가 어느 쪽으로 갔는지 표시하는 페이크(입력 토큰을 kakaoId로 되돌려 라우팅을 관측). */
    private static KakaoTokenVerifier tagging(String tag) {
        return token -> new KakaoAccount(tag + ":" + token);
    }

    @Test
    void stub_접두_토큰은_스텁_검증기로_위임한다() {
        var delegating = new DelegatingKakaoTokenVerifier(new StubKakaoTokenVerifier(), tagging("REAL"));

        KakaoAccount account = delegating.verify("stub:dev-user-1");

        // 스텁 규약대로 접두 제거된 fake id가 그대로 반환 → 스텁 경로 확인.
        assertThat(account.kakaoId()).isEqualTo("dev-user-1");
    }

    @Test
    void 접두가_없으면_실_검증기로_위임한다() {
        var delegating = new DelegatingKakaoTokenVerifier(new StubKakaoTokenVerifier(), tagging("REAL"));

        KakaoAccount account = delegating.verify("real-kakao-access-token");

        // REAL 페이크가 태깅한 값이 나옴 → 실 경로 확인(스텁은 stub: 아니면 예외였을 것).
        assertThat(account.kakaoId()).isEqualTo("REAL:real-kakao-access-token");
    }

    @Test
    void stub_접두지만_형식이_틀리면_스텁이_거부한다() {
        var delegating = new DelegatingKakaoTokenVerifier(new StubKakaoTokenVerifier(), tagging("REAL"));

        // "stub:" 접두라 스텁으로 라우팅되지만 fake id가 비어 스텁이 거부 → 실로 넘어가지 않음.
        assertThatThrownBy(() -> delegating.verify("stub:"))
                .isInstanceOf(KakaoTokenInvalidException.class);
    }
}
