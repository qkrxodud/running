package com.runningcrew.race.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 상태 전이 매트릭스 <b>전수(seed)</b> — 설계 22 §2.4 / session-api.md §8. 합법 전이 결과 상태와
 * 불법 전이 예외를 박제한다. 골든 픽스처 카탈로그는 test-engineer B2-T2가 확장한다. 순수 함수.
 */
class RaceSessionPolicyTest {

    @Test
    void open은_DRAFT에서만_OPEN으로() {
        assertThat(RaceSessionPolicy.apply(RaceStatus.DRAFT, SessionCommand.OPEN))
                .isEqualTo(RaceStatus.OPEN);
        for (RaceStatus s : new RaceStatus[] {RaceStatus.OPEN, RaceStatus.RUNNING,
                RaceStatus.FINALIZING, RaceStatus.COMPLETED, RaceStatus.CANCELLED}) {
            assertIllegal(s, SessionCommand.OPEN);
        }
    }

    @Test
    void register는_OPEN에서만_상태불변() {
        assertThat(RaceSessionPolicy.apply(RaceStatus.OPEN, SessionCommand.REGISTER))
                .isEqualTo(RaceStatus.OPEN);
        for (RaceStatus s : new RaceStatus[] {RaceStatus.DRAFT, RaceStatus.RUNNING,
                RaceStatus.FINALIZING, RaceStatus.COMPLETED, RaceStatus.CANCELLED}) {
            assertIllegal(s, SessionCommand.REGISTER);
        }
    }

    @Test
    void start는_OPEN과_RUNNING에서_RUNNING() {
        assertThat(RaceSessionPolicy.apply(RaceStatus.OPEN, SessionCommand.START))
                .isEqualTo(RaceStatus.RUNNING);
        assertThat(RaceSessionPolicy.apply(RaceStatus.RUNNING, SessionCommand.START))
                .isEqualTo(RaceStatus.RUNNING);
        for (RaceStatus s : new RaceStatus[] {RaceStatus.DRAFT, RaceStatus.FINALIZING,
                RaceStatus.COMPLETED, RaceStatus.CANCELLED}) {
            assertIllegal(s, SessionCommand.START);
        }
    }

    @Test
    void cancel은_종료전_3상태에서_CANCELLED() {
        for (RaceStatus s : new RaceStatus[] {RaceStatus.DRAFT, RaceStatus.OPEN, RaceStatus.RUNNING}) {
            assertThat(RaceSessionPolicy.apply(s, SessionCommand.CANCEL))
                    .isEqualTo(RaceStatus.CANCELLED);
        }
        for (RaceStatus s : new RaceStatus[] {RaceStatus.FINALIZING, RaceStatus.COMPLETED,
                RaceStatus.CANCELLED}) {
            assertIllegal(s, SessionCommand.CANCEL);
        }
    }

    private static void assertIllegal(RaceStatus status, SessionCommand command) {
        assertThatThrownBy(() -> RaceSessionPolicy.apply(status, command))
                .isInstanceOf(IllegalSessionTransitionException.class);
    }
}
