package com.runningcrew.user.adapter.out.kakao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 토큰 검증 어댑터 설정(env 외부화) — base-url·타임아웃만.
 *
 * <p>앱 키는 없다: {@code /v2/user/me}는 사용자 액세스 토큰만으로 호출되므로 서버에 저장할 카카오 자격이 없다.
 * 타임아웃 기본값은 연결 3s / 응답 5s(과제 요구) — kapi 지연이 로그인 트랜잭션을 오래 붙잡지 않도록 한다.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public KakaoProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://kapi.kakao.com";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
