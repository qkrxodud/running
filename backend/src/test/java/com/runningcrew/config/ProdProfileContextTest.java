package com.runningcrew.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.race.application.DevCourseSeeder;
import com.runningcrew.support.AbstractMySqlIntegrationTest;
import com.runningcrew.user.adapter.out.kakao.RealKakaoTokenVerifier;
import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * prod 프로필 컨텍스트 로드 검증 — 실 카카오 어댑터 배선 후 <b>prod가 부팅 가능</b>해졌음을 박제한다.
 *
 * <p>이전(스텁만 존재)에는 prod에 {@code KakaoTokenVerifier} 빈이 없어 포트 주입 실패로 의도적 fail-fast였다.
 * 이제 {@link RealKakaoTokenVerifier}(@Profile prod, KakaoVerifierConfig)가 포트를 채우므로 prod가 정상 부팅한다.
 * 동시에 dev 전용 요소(DevCourseSeeder)는 prod에 유출되지 않음(빈 부재)을 확인한다.
 *
 * <p>실 MySQL 8(Testcontainers, 부모 싱글턴 컨테이너 재사용). JWT_SECRET은 prod에 기본값이 없으므로 명시 주입한다.
 * 카카오 앱 키 설정은 필요 없다(user/me는 사용자 토큰만으로 호출) — 검증기 빈은 base-url·타임아웃만으로 생성된다.
 */
@ActiveProfiles(value = "prod", inheritProfiles = false)
class ProdProfileContextTest extends AbstractMySqlIntegrationTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "prod-context-test-secret-0123456789abcdef0123456789ab");
    }

    @Autowired
    ApplicationContext context;

    @Test
    void 실_카카오_검증기가_prod에서_활성이라_부팅에_성공한다() {
        assertThat(context.getBean(KakaoTokenVerifier.class))
                .isInstanceOf(RealKakaoTokenVerifier.class);
    }

    @Test
    void DevCourseSeeder는_prod에서_주입되지_않는다() {
        assertThat(context.getBeansOfType(DevCourseSeeder.class)).isEmpty();
    }
}
