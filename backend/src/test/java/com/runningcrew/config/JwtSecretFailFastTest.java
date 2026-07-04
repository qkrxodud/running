package com.runningcrew.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.user.adapter.out.token.JwtTokenProvider;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * JWT_SECRET fail-fast 검증(환경 분리 과제 §5) — MySQL·풀부팅 없이 빠르게.
 *
 * <p>sandbox/prod 프로필은 {@code application.yml}에 jwt.secret 기본값을 두지 않는다(베이스 {@code ${JWT_SECRET:}}만).
 * 따라서 env JWT_SECRET이 없으면 jwt.secret이 빈 문자열이 되어 {@link JwtTokenProvider} 생성자가 부팅을 거부한다.
 * 이 테스트는 그 fail-fast 게이트 자체를 검증한다(빈 시크릿 → 컨텍스트 로드 실패, 유효 시크릿 → 성공).
 */
class JwtSecretFailFastTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues(
                    "jwt.access-ttl-seconds=1800",
                    "jwt.refresh-ttl-seconds=2592000")
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(JwtTokenProvider.class);

    @Test
    void 시크릿이_없으면_부팅에_실패한다() {
        runner.withPropertyValues("jwt.secret=")   // env JWT_SECRET 미설정 상황(sandbox/prod 기본값 없음)
                .run(ctx -> assertThat(ctx)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("JWT_SECRET"));
    }

    @Test
    void 유효한_시크릿이면_부팅에_성공한다() {
        runner.withPropertyValues("jwt.secret=valid-sandbox-secret-0123456789abcdef0123456789")
                .run(ctx -> assertThat(ctx)
                        .hasNotFailed()
                        .hasSingleBean(JwtTokenProvider.class));
    }
}
