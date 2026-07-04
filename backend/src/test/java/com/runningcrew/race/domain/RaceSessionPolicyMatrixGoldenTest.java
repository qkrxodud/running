package com.runningcrew.race.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * RaceSession 상태머신 <b>전수 매트릭스 골든</b>(6상태 × 4명령 = 24셀 전량 명시).
 *
 * <p>기대값은 설계 문서 {@code _workspace/22_analyst_design_B2.md} §2.4 매트릭스에서 도출한다
 * (구현 역산 금지). {@code null} 기대는 불법 전이 = {@link IllegalSessionTransitionException}.
 * seed {@code RaceSessionPolicyTest}가 명령 축으로 검증하는 것과 달리, 여기서는 24셀을 한 표로
 * 못박아 어떤 셀이 바뀌어도 그 좌표(상태·명령)가 실패 메시지에 드러나게 한다.
 *
 * <pre>
 * 명령 \ status   DRAFT      OPEN        RUNNING     FINALIZING  COMPLETED  CANCELLED
 * OPEN            →OPEN      illegal     illegal     illegal     illegal    illegal
 * REGISTER        illegal    →OPEN(불변) illegal     illegal     illegal    illegal
 * START           illegal    →RUNNING    →RUNNING    illegal     illegal    illegal
 * CANCEL          →CANCELLED →CANCELLED  →CANCELLED  illegal     illegal    illegal
 * </pre>
 */
class RaceSessionPolicyMatrixGoldenTest {

    /** 설계 §2.4 전수 표 — {@code (현재상태, 명령, 기대상태 | null=불법)}. */
    static List<Arguments> matrix() {
        List<Arguments> rows = new ArrayList<>();

        // OPEN: DRAFT 에서만 → OPEN, 나머지 전부 불법
        rows.add(Arguments.of(RaceStatus.DRAFT, SessionCommand.OPEN, RaceStatus.OPEN));
        rows.add(Arguments.of(RaceStatus.OPEN, SessionCommand.OPEN, null));
        rows.add(Arguments.of(RaceStatus.RUNNING, SessionCommand.OPEN, null));
        rows.add(Arguments.of(RaceStatus.FINALIZING, SessionCommand.OPEN, null));
        rows.add(Arguments.of(RaceStatus.COMPLETED, SessionCommand.OPEN, null));
        rows.add(Arguments.of(RaceStatus.CANCELLED, SessionCommand.OPEN, null));

        // REGISTER: OPEN 에서만 상태 불변(→OPEN), 나머지 전부 불법
        rows.add(Arguments.of(RaceStatus.DRAFT, SessionCommand.REGISTER, null));
        rows.add(Arguments.of(RaceStatus.OPEN, SessionCommand.REGISTER, RaceStatus.OPEN));
        rows.add(Arguments.of(RaceStatus.RUNNING, SessionCommand.REGISTER, null));
        rows.add(Arguments.of(RaceStatus.FINALIZING, SessionCommand.REGISTER, null));
        rows.add(Arguments.of(RaceStatus.COMPLETED, SessionCommand.REGISTER, null));
        rows.add(Arguments.of(RaceStatus.CANCELLED, SessionCommand.REGISTER, null));

        // START: OPEN(첫)·RUNNING(멱등) → RUNNING, 나머지 전부 불법
        rows.add(Arguments.of(RaceStatus.DRAFT, SessionCommand.START, null));
        rows.add(Arguments.of(RaceStatus.OPEN, SessionCommand.START, RaceStatus.RUNNING));
        rows.add(Arguments.of(RaceStatus.RUNNING, SessionCommand.START, RaceStatus.RUNNING));
        rows.add(Arguments.of(RaceStatus.FINALIZING, SessionCommand.START, null));
        rows.add(Arguments.of(RaceStatus.COMPLETED, SessionCommand.START, null));
        rows.add(Arguments.of(RaceStatus.CANCELLED, SessionCommand.START, null));

        // CANCEL: 종료 전 3상태(DRAFT/OPEN/RUNNING) → CANCELLED, 종료 3상태는 불법
        rows.add(Arguments.of(RaceStatus.DRAFT, SessionCommand.CANCEL, RaceStatus.CANCELLED));
        rows.add(Arguments.of(RaceStatus.OPEN, SessionCommand.CANCEL, RaceStatus.CANCELLED));
        rows.add(Arguments.of(RaceStatus.RUNNING, SessionCommand.CANCEL, RaceStatus.CANCELLED));
        rows.add(Arguments.of(RaceStatus.FINALIZING, SessionCommand.CANCEL, null));
        rows.add(Arguments.of(RaceStatus.COMPLETED, SessionCommand.CANCEL, null));
        rows.add(Arguments.of(RaceStatus.CANCELLED, SessionCommand.CANCEL, null));

        return rows;
    }

    @ParameterizedTest(name = "[{0} × {1}] → {2}")
    @MethodSource("matrix")
    @DisplayName("설계 §2.4 상태 전이 매트릭스 24셀 전수")
    void 상태_전이_매트릭스_전수(RaceStatus current, SessionCommand command, RaceStatus expected) {
        if (expected == null) {
            assertThatThrownBy(() -> RaceSessionPolicy.apply(current, command))
                    .as("불법 전이여야 함: %s × %s", current, command)
                    .isInstanceOf(IllegalSessionTransitionException.class);
        } else {
            assertThat(RaceSessionPolicy.apply(current, command))
                    .as("합법 전이 결과 상태: %s × %s", current, command)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("표가 6상태 × 4명령 = 24셀을 빠짐없이 덮는지 (누락 방지 가드)")
    void 매트릭스는_24셀을_모두_덮는다() {
        assertThat(matrix()).hasSize(RaceStatus.values().length * SessionCommand.values().length);
    }
}
