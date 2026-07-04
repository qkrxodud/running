package com.runningcrew.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.race.application.DevCourseSeeder;
import com.runningcrew.support.AbstractMySqlIntegrationTest;
import com.runningcrew.user.adapter.out.kakao.StubKakaoTokenVerifier;
import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * sandbox 프로필 컨텍스트 로드 검증(환경 분리 과제 §5).
 *
 * <p>실 MySQL 8(Testcontainers, 부모 싱글턴 컨테이너 재사용) 위에서 {@code sandbox} 프로필로 부팅해:
 * <ul>
 *   <li>스텁 카카오 검증기가 활성(sandbox = 크루원 테스트용 스텁 로그인 ON)임을,
 *   <li>DevCourseSeeder가 활성(sandbox = 시더 ON)임을,
 *   <li>prod의 "스텁 빈 부재 fail-fast"가 sandbox에서 오작동하지 않음(빈이 정상 주입됨)을 확인한다.
 * </ul>
 * JWT_SECRET은 sandbox에 기본값이 없으므로 테스트가 명시 주입한다(주입하면 부팅 성공).
 */
@ActiveProfiles(value = "sandbox", inheritProfiles = false)
class SandboxProfileContextTest extends AbstractMySqlIntegrationTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        // sandbox 프로필엔 jwt.secret 기본값이 없음 → 부팅하려면 env/property로 반드시 주입해야 함(≥256bit).
        registry.add("jwt.secret", () -> "sandbox-context-test-secret-0123456789abcdef0123456789");
    }

    @Autowired
    ApplicationContext context;

    @Test
    void 스텁_카카오_검증기가_sandbox에서_활성이다() {
        assertThat(context.getBean(KakaoTokenVerifier.class))
                .isInstanceOf(StubKakaoTokenVerifier.class);
    }

    @Test
    void DevCourseSeeder가_sandbox에서_활성이다() {
        assertThat(context.getBeansOfType(DevCourseSeeder.class)).isNotEmpty();
    }
}
