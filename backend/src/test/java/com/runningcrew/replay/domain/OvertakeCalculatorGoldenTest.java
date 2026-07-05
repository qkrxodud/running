package com.runningcrew.replay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OvertakeCalculator} 경계 카탈로그 골든(설계 72 §3.2 — 부호반전 정의). seed({@code
 * OvertakeCalculatorTest}: 반전 1건·동시도달 비이벤트·DNF 범위밖 미발생)가 커버 못 한 <b>갭만</b> 추가.
 *
 * <p>프레임은 (t_ms, cum_dist_m)만 의미(lat/lng=0). T_u(d)=cum_dist 선형보간 도달시각.
 * 부호(T_A−T_B) 반전 지점이 추월(passer=뒤→앞). 손계산 가능한 소형 트랙.
 */
class OvertakeCalculatorGoldenTest {

    private static ReplayFrame f(long tMs, int cum) {
        return new ReplayFrame(tMs, 0, 0, cum, false);
    }

    private static ReplayParticipant p(long userId, List<ReplayFrame> frames) {
        return new ReplayParticipant(userId, "FINISHED",
                frames.get(frames.size() - 1).tMs(), frames, List.of());
    }

    @Test
    @DisplayName("재역전 — A↔B 두 번 교차하면 순서대로 2건(at_dist 오름차순)")
    void 재역전은_순서대로_2건() {
        // Δ=T_A−T_B: d500 -100(A앞) → d1000 +100(B앞, cross1: B가 A 추월) → d1500 -100(A앞, cross2: A가 B 추월)
        ReplayParticipant a = p(7L, List.of(f(0, 0), f(100, 500), f(400, 1000), f(500, 1500)));
        ReplayParticipant b = p(3L, List.of(f(0, 0), f(200, 500), f(300, 1000), f(600, 1500)));
        List<Overtake> o = OvertakeCalculator.computeOvertakes(List.of(a, b));
        assertThat(o).hasSize(2);
        assertThat(o.get(0).atDistM()).isLessThan(o.get(1).atDistM());   // d 오름차순
        assertThat(o.get(0).passerUserId()).isEqualTo(3L);   // 1차: B가 A
        assertThat(o.get(0).passedUserId()).isEqualTo(7L);
        assertThat(o.get(1).passerUserId()).isEqualTo(7L);   // 2차: A가 B(재역전)
        assertThat(o.get(1).passedUserId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("무추월 — 범위 겹쳐도 A가 전 구간 앞서면 이벤트 0")
    void 전구간_앞서면_무추월() {
        ReplayParticipant a = p(7L, List.of(f(0, 0), f(100, 500), f(200, 1000)));
        ReplayParticipant b = p(3L, List.of(f(0, 0), f(150, 500), f(300, 1000)));   // 항상 뒤
        assertThat(OvertakeCalculator.computeOvertakes(List.of(a, b))).isEmpty();
    }

    @Test
    @DisplayName("출발 직후 역전 — 작은 진행거리에서 교차")
    void 출발_직후_역전() {
        // d100 Δ=-90(A앞) → d200 Δ=+300(B앞) → 교차 거리는 [100,200] 구간(출발 직후)
        ReplayParticipant a = p(7L, List.of(f(0, 0), f(10, 100), f(500, 200)));
        ReplayParticipant b = p(3L, List.of(f(0, 0), f(100, 100), f(200, 200)));
        List<Overtake> o = OvertakeCalculator.computeOvertakes(List.of(a, b));
        assertThat(o).hasSize(1);
        assertThat(o.get(0).passerUserId()).isEqualTo(3L);
        assertThat(o.get(0).atDistM()).isLessThanOrEqualTo(200);   // 출발 직후
    }

    @Test
    @DisplayName("DNF 도달 범위 안에서는 추월 발생(범위 밖 미발생과 대칭)")
    void DNF_범위_안_추월_발생() {
        // 공통범위 [0,400]. d200 Δ=-90(A앞) → d400 Δ=+50(B앞) → B가 A 추월(범위 내).
        ReplayParticipant a = p(7L, List.of(f(0, 0), f(10, 200), f(200, 400), f(300, 1000)));
        ReplayParticipant bDnf = new ReplayParticipant(3L, "DNF", null,
                List.of(f(0, 0), f(100, 200), f(150, 400)), List.of());
        List<Overtake> o = OvertakeCalculator.computeOvertakes(List.of(a, bDnf));
        assertThat(o).hasSize(1);
        assertThat(o.get(0).passerUserId()).isEqualTo(3L);   // B(DNF)가 범위 내에서 A 추월
        assertThat(o.get(0).atDistM()).isBetween(200, 400);
    }

    @Test
    @DisplayName("진행거리 범위 미교집합(한쪽 0m) — 공통 도달 거리 없음 → 이벤트 0")
    void 범위_미교집합_이벤트_없음() {
        ReplayParticipant a = p(7L, List.of(f(0, 0), f(100, 500), f(200, 1000)));
        // 정지(누적 0 유지) — commonMax = min(1000, 0) = 0 → 미발생
        ReplayParticipant stuck = p(3L, List.of(f(0, 0), f(100, 0), f(200, 0)));
        assertThat(OvertakeCalculator.computeOvertakes(List.of(a, stuck))).isEmpty();
    }

    @Test
    @DisplayName("3인 쌍별 계산 — 각 쌍 독립 평가, 결과 정렬(at_dist↑·passer↑)")
    void 삼인_쌍별_계산() {
        // A 전구간 최고속(무추월 상대). B·C가 서로 한 번 교차. → 총 1건(B·C 쌍만).
        ReplayParticipant a = p(1L, List.of(f(0, 0), f(50, 500), f(100, 1000)));   // 최고속
        ReplayParticipant b = p(2L, List.of(f(0, 0), f(200, 500), f(500, 1000)));  // 초반 빠름
        ReplayParticipant c = p(3L, List.of(f(0, 0), f(400, 500), f(450, 1000)));  // 후반 빠름
        // B·C: d500 Δ=200-400=-200(B앞) → d1000 Δ=500-450=+50(C앞) → C가 B 추월.
        List<Overtake> o = OvertakeCalculator.computeOvertakes(List.of(a, b, c));
        assertThat(o).hasSize(1);
        assertThat(o.get(0).passerUserId()).isEqualTo(3L);   // C
        assertThat(o.get(0).passedUserId()).isEqualTo(2L);   // B
    }
}
