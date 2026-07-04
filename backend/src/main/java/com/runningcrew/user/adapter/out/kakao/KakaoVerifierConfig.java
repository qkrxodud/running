package com.runningcrew.user.adapter.out.kakao;

import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 카카오 토큰 검증기 프로필 배선(오케스트레이터 확정 설계).
 *
 * <p>프로필별로 {@link KakaoTokenVerifier} 빈을 <b>정확히 1개</b>만 노출한다(주입 모호성 없음):
 * <ul>
 *   <li><b>local/dev/sandbox</b> = {@link DelegatingKakaoTokenVerifier}: {@code stub:} 접두 토큰은
 *       {@link StubKakaoTokenVerifier}로, 그 외는 실 kapi({@link RealKakaoTokenVerifier})로 위임 — 스텁·실 공존.
 *   <li><b>prod</b> = {@link RealKakaoTokenVerifier}만 — 스텁 빈 자체가 없어 운영 유출 불가.
 * </ul>
 *
 * <p>이제 prod도 부팅 가능하다: 기존 "스텁 빈 부재 → 포트 주입 실패 fail-fast"를 Real 어댑터가 대체한다.
 * 카카오 앱 키는 불필요(user/me는 사용자 토큰만으로 호출) — {@link KakaoProperties}는 base-url·타임아웃만 외부화.
 */
@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoVerifierConfig {

    @Bean
    @Profile("prod")
    KakaoTokenVerifier prodKakaoTokenVerifier(KakaoProperties props) {
        return buildReal(props);
    }

    @Bean
    @Profile({"local", "dev", "sandbox"})
    KakaoTokenVerifier delegatingKakaoTokenVerifier(KakaoProperties props) {
        return new DelegatingKakaoTokenVerifier(new StubKakaoTokenVerifier(), buildReal(props));
    }

    private RealKakaoTokenVerifier buildReal(KakaoProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
        return new RealKakaoTokenVerifier(restClient);
    }
}
