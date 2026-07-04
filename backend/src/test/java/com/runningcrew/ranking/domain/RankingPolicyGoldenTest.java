package com.runningcrew.ranking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RankingPolicy} 경계 카탈로그 골든(설계 42 §5.2). seed({@code RankingPolicyTest}: 2자 동률 1,1,3 /
 * DNF·DNS 하단 / PB 첫완주·갱신·미갱신·DNF)가 커버 못 한 <b>갭만</b> 추가한다. 기대값은 계획서 §5.4.
 */
class RankingPolicyGoldenTest {

    private static RankedEntry byUser(List<RankedEntry> r, long userId) {
        return r.stream().filter(e -> e.userId() == userId).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("3자 동률은 공동 1위 셋 + 다음은 4위 (1,1,1,4)")
    void 삼자_동률_1_1_1_4() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(1L, ResultStatus.FINISHED, 1500, null),
                new RankingInput(2L, ResultStatus.FINISHED, 1500, null),
                new RankingInput(3L, ResultStatus.FINISHED, 1500, null),
                new RankingInput(4L, ResultStatus.FINISHED, 1700, null)));
        assertThat(r).extracting(RankedEntry::userId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(r).extracting(RankedEntry::rank).containsExactly(1, 1, 1, 4);
    }

    @Test
    @DisplayName("동률이 선두가 아니어도 건너뜀 유지 (1,2,2,4)")
    void 중간_동률_1_2_2_4() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(1L, ResultStatus.FINISHED, 1400, null),
                new RankingInput(2L, ResultStatus.FINISHED, 1500, null),
                new RankingInput(3L, ResultStatus.FINISHED, 1500, null),
                new RankingInput(4L, ResultStatus.FINISHED, 1700, null)));
        assertThat(r).extracting(RankedEntry::rank).containsExactly(1, 2, 2, 4);
    }

    @Test
    @DisplayName("동률 내부 정렬은 userId 오름차순으로 결정적")
    void 동률_내부_userId_오름차순() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(3L, ResultStatus.FINISHED, 1500, null),
                new RankingInput(1L, ResultStatus.FINISHED, 1500, null)));
        assertThat(r).extracting(RankedEntry::userId).containsExactly(1L, 3L);   // 입력순 아님
        assertThat(r).extracting(RankedEntry::rank).containsExactly(1, 1);
    }

    @Test
    @DisplayName("전원 DNF → 모두 rank null, userId 오름차순 하단")
    void 전원_DNF() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(3L, ResultStatus.DNF, null, null),
                new RankingInput(1L, ResultStatus.DNF, null, null),
                new RankingInput(2L, ResultStatus.DNF, null, null)));
        assertThat(r).extracting(RankedEntry::userId).containsExactly(1L, 2L, 3L);
        assertThat(r).extracting(RankedEntry::rank).containsExactly(null, null, null);
    }

    @Test
    @DisplayName("참가자 1명 완주 → rank 1, 첫 완주라 PB")
    void 참가자_1명_완주() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(7L, ResultStatus.FINISHED, 1502, null)));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).rank()).isEqualTo(1);
        assertThat(r.get(0).isPb()).isTrue();
    }

    @Test
    @DisplayName("PB 경계: 과거 기록과 동일하면 미갱신(is_pb=false) — 개선일 때만 PB")
    void PB_동일기록은_미갱신() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(1L, ResultStatus.FINISHED, 1500, 1500)));   // record == priorPb
        assertThat(r.get(0).isPb()).isFalse();   // < 가 아니라 == 이므로 갱신 아님
    }

    @Test
    @DisplayName("PB 경계: 과거보다 1초 빠르면 갱신(is_pb=true)")
    void PB_1초_빨라도_갱신() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(1L, ResultStatus.FINISHED, 1499, 1500)));
        assertThat(r.get(0).isPb()).isTrue();
    }

    @Test
    @DisplayName("DNF는 과거 PB가 있어도 is_pb 항상 false")
    void DNF는_priorPb_있어도_false() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(1L, ResultStatus.DNF, null, 1400)));
        assertThat(byUser(r, 1L).isPb()).isFalse();
        assertThat(byUser(r, 1L).rank()).isNull();
    }

    @Test
    @DisplayName("DNS는 is_pb false, rank null")
    void DNS_is_pb_false() {
        List<RankedEntry> r = RankingPolicy.rank(List.of(
                new RankingInput(1L, ResultStatus.DNS, null, null)));
        assertThat(r.get(0).isPb()).isFalse();
        assertThat(r.get(0).rank()).isNull();
        assertThat(r.get(0).status()).isEqualTo(ResultStatus.DNS);
    }
}
