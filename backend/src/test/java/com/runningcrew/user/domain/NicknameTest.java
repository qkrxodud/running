package com.runningcrew.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Nickname#normalize} 순수 정규화·검증 골든 테스트 (배치 B1).
 *
 * <p>기대값 근거: user-api.md v0.1 §2(닉네임: trim 후 1~30자, 제어문자 금지, 유일성 없음),
 * 13_backend_report_B1.md §3.3. 기대값은 계약에서 도출(구현 역산 아님).
 */
class NicknameTest {

    @Nested
    @DisplayName("정상 — trim 후 1~30자, 제어문자 없음")
    class Valid {

        @Test
        @DisplayName("앞뒤 공백은 제거되고 내부 문자열이 그대로 보존된다")
        void 앞뒤_공백_트리밍() {
            assertThat(Nickname.normalize("  민수  ")).isEqualTo("민수");
        }

        @Test
        @DisplayName("내부 공백은 제어문자가 아니므로 보존된다")
        void 내부_공백_보존() {
            assertThat(Nickname.normalize("김 민수")).isEqualTo("김 민수");
        }

        @Test
        @DisplayName("trim 후 정확히 1자(하한 경계)는 허용된다")
        void 하한_1자_허용() {
            assertThat(Nickname.normalize("김")).isEqualTo("김");
        }

        @Test
        @DisplayName("trim 후 정확히 30자(상한 경계)는 허용된다")
        void 상한_30자_허용() {
            String name = "가".repeat(30);
            assertThat(Nickname.normalize(name)).isEqualTo(name);
        }

        @Test
        @DisplayName("유일성은 검증 대상이 아니므로 동일 문자열도 그대로 통과한다")
        void 유일성_없음() {
            assertThat(Nickname.normalize("민수")).isEqualTo("민수");
        }
    }

    @Nested
    @DisplayName("길이 위반 — InvalidNicknameException")
    class LengthViolation {

        @Test
        @DisplayName("null은 필수 위반으로 예외")
        void null은_예외() {
            assertThatThrownBy(() -> Nickname.normalize(null))
                    .isInstanceOf(InvalidNicknameException.class);
        }

        @Test
        @DisplayName("빈 문자열은 trim 후 0자 → 예외")
        void 빈_문자열은_예외() {
            assertThatThrownBy(() -> Nickname.normalize(""))
                    .isInstanceOf(InvalidNicknameException.class);
        }

        @Test
        @DisplayName("공백만 있는 문자열은 trim 후 0자 → 예외")
        void 공백만은_예외() {
            assertThatThrownBy(() -> Nickname.normalize("     "))
                    .isInstanceOf(InvalidNicknameException.class);
        }

        @Test
        @DisplayName("trim 후 31자(상한 초과)는 예외")
        void 상한_초과_31자는_예외() {
            assertThatThrownBy(() -> Nickname.normalize("가".repeat(31)))
                    .isInstanceOf(InvalidNicknameException.class);
        }

        @Test
        @DisplayName("trim 후 30자가 되도록 공백을 덧댄 32자 입력은 허용된다(길이는 trim 기준)")
        void 공백_포함_길이는_trim_기준() {
            String padded = " " + "가".repeat(30) + " "; // 32자, trim 후 30자
            assertThat(Nickname.normalize(padded)).isEqualTo("가".repeat(30));
        }
    }

    @Nested
    @DisplayName("제어문자 위반 — InvalidNicknameException")
    class ControlCharViolation {

        @Test
        @DisplayName("내부 탭 문자는 제어문자이므로 예외")
        void 내부_탭은_예외() {
            assertThatThrownBy(() -> Nickname.normalize("민\t수"))
                    .isInstanceOf(InvalidNicknameException.class);
        }

        @Test
        @DisplayName("내부 개행 문자는 제어문자이므로 예외")
        void 내부_개행은_예외() {
            assertThatThrownBy(() -> Nickname.normalize("민\n수"))
                    .isInstanceOf(InvalidNicknameException.class);
        }

        @Test
        @DisplayName("널 문자(U+0000)는 제어문자이므로 예외")
        void 널문자는_예외() {
            assertThatThrownBy(() -> Nickname.normalize("민\u0000수"))
                    .isInstanceOf(InvalidNicknameException.class);
        }

        @Test
        @DisplayName("앞뒤에만 붙은 탭/개행은 trim으로 제거되어 예외가 아니다")
        void 앞뒤_탭개행은_trim되어_통과() {
            assertThat(Nickname.normalize("\t민수\n")).isEqualTo("민수");
        }

        @Test
        @DisplayName("일반 문장부호·숫자는 제어문자가 아니므로 통과한다")
        void 일반_기호는_통과() {
            Throwable t = catchThrowable(() -> Nickname.normalize("run_99!"));
            assertThat(t).isNull();
        }
    }
}
