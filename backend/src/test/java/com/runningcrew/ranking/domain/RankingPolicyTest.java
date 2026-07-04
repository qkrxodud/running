package com.runningcrew.ranking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RankingPolicy 순수 함수 <b>seed 테스트</b>(A7) — 경계 카탈로그 확장은 test-engineer 소관.
 * 계획서 §5.4: 오름차순·동률 공동순위 건너뜀(1,1,3)·DNF/DNS 하단·PB(완주만).
 */
class RankingPolicyTest {

    @Test
    void 동률은_공동순위_다음_건너뜀_1_1_3() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(1L, ResultStatus.FINISHED, 1502, null),
                new RankingInput(2L, ResultStatus.FINISHED, 1502, null),
                new RankingInput(3L, ResultStatus.FINISHED, 1640, null)));
        assertThat(r).extracting(RankedEntry::userId).containsExactly(1L, 2L, 3L);
        assertThat(r).extracting(RankedEntry::rank).containsExactly(1, 1, 3);
    }

    @Test
    void DNF_DNS는_rank_null이고_완주_뒤_DNF_DNS_순() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(9L, ResultStatus.DNS, null, null),
                new RankingInput(8L, ResultStatus.DNF, null, null),
                new RankingInput(5L, ResultStatus.FINISHED, 1640, null)));
        assertThat(r).extracting(RankedEntry::userId).containsExactly(5L, 8L, 9L);
        assertThat(r).extracting(RankedEntry::status).containsExactly(
                ResultStatus.FINISHED, ResultStatus.DNF, ResultStatus.DNS);
        assertThat(r).extracting(RankedEntry::rank).containsExactly(1, null, null);
    }

    @Test
    void PB는_완주만_첫완주_또는_기록갱신_시_true_DNF는_false() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(1L, ResultStatus.FINISHED, 1500, null),   // 첫 완주 → PB
                new RankingInput(2L, ResultStatus.FINISHED, 1400, 1450),   // 갱신 → PB
                new RankingInput(3L, ResultStatus.FINISHED, 1600, 1550),   // 미갱신 → not PB
                new RankingInput(4L, ResultStatus.DNF, null, null)));       // DNF → false
        assertThat(byUser(r, 1L).isPb()).isTrue();
        assertThat(byUser(r, 2L).isPb()).isTrue();
        assertThat(byUser(r, 3L).isPb()).isFalse();
        assertThat(byUser(r, 4L).isPb()).isFalse();
    }

    private static RankedEntry byUser(List<RankedEntry> r, long userId) {
        return r.stream().filter(e -> e.userId() == userId).findFirst().orElseThrow();
    }
}
