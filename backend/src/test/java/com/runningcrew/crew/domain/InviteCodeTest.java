package com.runningcrew.crew.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link InviteCode}의 순수 만료·소진 판정 골든 테스트 (배치 B1, test-engineer).
 *
 * <p>기대값 근거: crew-api.md v0.2 §5(만료·소진 오류), 13_backend_report_B1.md §3.1,
 * domain-model 스킬 Crew 불변식({@code used_count <= max_uses}, 만료 UTC 판정).
 * 기대값은 계약·설계에서 도출했으며 구현 실행 결과를 복사하지 않았다.
 */
class InviteCodeTest {

    private static InviteCode codeExpiringAt(Instant expiresAt) {
        return new InviteCode("VVQQP6", 12L, expiresAt, 10, 0);
    }

    private static InviteCode codeWithUses(int maxUses, int usedCount) {
        // 만료는 이 그룹의 관심사가 아니므로 먼 미래로 고정.
        return new InviteCode("VVQQP6", 12L, Instant.parse("2999-01-01T00:00:00Z"), maxUses, usedCount);
    }

    @Nested
    @DisplayName("isExpired — 참가는 expires_at > now 일 때만 유효, 경계는 만료")
    class IsExpired {

        // 계약 예시(13 §3.1)와 동일한 기준시각.
        private final Instant expiresAt = Instant.parse("2026-07-07T00:00:00Z");
        private final InviteCode code = codeExpiringAt(expiresAt);

        @Test
        @DisplayName("now가 expires_at 직전(1초 전)이면 만료 아님")
        void 직전_1초는_만료_아님() {
            assertThat(code.isExpired(Instant.parse("2026-07-06T23:59:59Z"))).isFalse();
        }

        @Test
        @DisplayName("now가 expires_at과 정확히 같으면 만료(경계는 만료)")
        void 경계값_동일시각은_만료() {
            assertThat(code.isExpired(expiresAt)).isTrue();
        }

        @Test
        @DisplayName("now가 expires_at 직후(1밀리초 후)면 만료")
        void 직후_1밀리초는_만료() {
            assertThat(code.isExpired(expiresAt.plusMillis(1))).isTrue();
        }

        @Test
        @DisplayName("나노초 해상도: expires_at 1나노초 전은 만료 아님, 1나노초 후는 만료")
        void 나노초_경계() {
            assertThat(code.isExpired(expiresAt.minusNanos(1))).isFalse();
            assertThat(code.isExpired(expiresAt.plusNanos(1))).isTrue();
        }

        @Test
        @DisplayName("극단 과거(Instant.MIN)는 만료 아님")
        void 극단_과거는_만료_아님() {
            assertThat(code.isExpired(Instant.MIN)).isFalse();
        }

        @Test
        @DisplayName("극단 미래(Instant.MAX)는 만료")
        void 극단_미래는_만료() {
            assertThat(code.isExpired(Instant.MAX)).isTrue();
        }
    }

    @Nested
    @DisplayName("isExhausted — used_count >= max_uses 이면 소진")
    class IsExhausted {

        @Test
        @DisplayName("used_count가 max_uses보다 작으면(5중 4) 소진 아님")
        void 미만은_소진_아님() {
            assertThat(codeWithUses(5, 4).isExhausted()).isFalse();
        }

        @Test
        @DisplayName("used_count가 max_uses와 같으면(5중 5) 소진 (경계)")
        void 경계_동일은_소진() {
            assertThat(codeWithUses(5, 5).isExhausted()).isTrue();
        }

        @Test
        @DisplayName("used_count가 max_uses를 초과해도(5중 6) 소진 (>= 이므로)")
        void 초과도_소진() {
            assertThat(codeWithUses(5, 6).isExhausted()).isTrue();
        }

        @Test
        @DisplayName("1회용(max_uses=1): 미사용은 소진 아님, 1회 사용 후 소진")
        void 일회용_경계() {
            assertThat(codeWithUses(1, 0).isExhausted()).isFalse();
            assertThat(codeWithUses(1, 1).isExhausted()).isTrue();
        }

        @Test
        @DisplayName("incrementUse 1회 후 1회용 코드는 소진 상태가 된다")
        void incrementUse_후_소진() {
            InviteCode code = codeWithUses(1, 0);
            code.incrementUse();
            assertThat(code.getUsedCount()).isEqualTo(1);
            assertThat(code.isExhausted()).isTrue();
        }

        // max_uses=0은 계약(crew-api §4: max_uses 1~100)상 어댑터에서 400으로 차단되는 무효 입력이다.
        // 순수 함수 자체는 검증하지 않으므로, "0회 허용 = 항상 소진"이라는 논리적 귀결(0>=0)을 문서화만 한다.
        // 이는 구현 역산이 아니라 max_uses의 의미에서 도출한 값이다.
        @Test
        @DisplayName("max_uses=0(계약상 무효 입력)은 순수 함수 수준에서 항상 소진으로 귀결")
        void max_0은_항상_소진_계약상_어댑터_차단() {
            assertThat(codeWithUses(0, 0).isExhausted()).isTrue();
        }
    }
}
