package com.runningcrew.replay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A2 추월 seed(골든 확장은 test-engineer). 부호 반전=추월 / 동시도달=비이벤트 / DNF 범위 밖 미발생.
 * 프레임은 (t_ms, cum_dist_m)만 의미 — lat/lng는 0.
 */
class OvertakeCalculatorTest {

    private static ReplayFrame f(long tMs, int cum) {
        return new ReplayFrame(tMs, 0, 0, cum, false);
    }

    private static ReplayParticipant p(long userId, List<ReplayFrame> frames) {
        return new ReplayParticipant(userId, "FINISHED", frames.get(frames.size() - 1).tMs(),
                frames, List.of());
    }

    @Test
    void 부호_반전이면_추월_1건_passer_passed_정확() {
        // A: 초반 앞(0→500m 빠름), 후반 느림. B: 초반 느림, 후반 빠름 → 어딘가에서 B가 A 추월.
        ReplayParticipant a = p(7L, List.of(f(0, 0), f(100, 500), f(400, 1000)));
        ReplayParticipant b = p(3L, List.of(f(0, 0), f(200, 500), f(300, 1000)));
        // d=500: T_A=100 < T_B=200 (A 앞) → Δ<0. d=1000: T_A=400 > T_B=300 (B 앞) → Δ>0. 반전 → B가 A 추월.

        List<Overtake> overtakes = OvertakeCalculator.computeOvertakes(List.of(a, b));

        assertThat(overtakes).hasSize(1);
        Overtake o = overtakes.get(0);
        assertThat(o.passerUserId()).isEqualTo(3L);   // B
        assertThat(o.passedUserId()).isEqualTo(7L);   // A
        assertThat(o.atDistM()).isBetween(500, 1000);
    }

    @Test
    void 동시_도달은_추월_아님() {
        // 두 참가자가 모든 거리에서 동일 시각 도달(Δ=0 유지) → 부호 반전 없음.
        ReplayParticipant a = p(7L, List.of(f(0, 0), f(100, 500), f(200, 1000)));
        ReplayParticipant b = p(3L, List.of(f(0, 0), f(100, 500), f(200, 1000)));

        assertThat(OvertakeCalculator.computeOvertakes(List.of(a, b))).isEmpty();
    }

    @Test
    void DNF_공통범위_밖은_미발생() {
        // A는 1000m까지, B(DNF)는 400m까지만. 공통범위 [0,400]에서 A가 계속 앞(반전 없음) → 추월 0.
        ReplayParticipant a = p(7L, List.of(f(0, 0), f(80, 400), f(200, 1000)));
        ReplayParticipant bDnf = new ReplayParticipant(3L, "DNF", null,
                List.of(f(0, 0), f(200, 400)), List.of());

        assertThat(OvertakeCalculator.computeOvertakes(List.of(a, bDnf))).isEmpty();
    }
}
