package com.runningcrew.replay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.tracking.domain.TrackSegment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PaceColorizer} 경계 카탈로그 골든(설계 72 §3.3). seed({@code PaceColorizerTest}: 240 경계·200→0·
 * 500→4·마지막 미완)가 커버 못 한 <b>갭만</b> 추가. 경계 [240,300,360,420]: {@code pace < b[i]} → 버킷 i.
 */
class PaceColorizerGoldenTest {

    private static TrackSegment seg(int index, int pace) {
        return new TrackSegment(index, index * 500, index * 500 + 500, 100, pace);
    }

    @Test
    @DisplayName("각 버킷 경계 정확값 — 300→2·360→3·420→4(등호는 위 버킷)")
    void 각_경계_정확값() {
        List<ReplaySegmentColor> c = PaceColorizer.colorize(
                List.of(seg(0, 300), seg(1, 360), seg(2, 420)), ColorParams.defaults());
        assertThat(c).extracting(ReplaySegmentColor::colorBucket).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("경계 직전값 — 299→1·359→2·419→3(미만은 아래 버킷)")
    void 경계_직전값() {
        List<ReplaySegmentColor> c = PaceColorizer.colorize(
                List.of(seg(0, 299), seg(1, 359), seg(2, 419)), ColorParams.defaults());
        assertThat(c).extracting(ReplaySegmentColor::colorBucket).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("극단 페이스 — 0(초고속)→0, 아주 느림(9999)→최상단 버킷 4")
    void 극단_페이스() {
        List<ReplaySegmentColor> c = PaceColorizer.colorize(
                List.of(seg(0, 0), seg(1, 9999)), ColorParams.defaults());
        assertThat(c.get(0).colorBucket()).isZero();
        assertThat(c.get(1).colorBucket()).isEqualTo(4);   // 경계 4개 → 최대 버킷 4
    }

    @Test
    @DisplayName("전 구간 동일 페이스 → 모두 동일 버킷")
    void 전구간_동일_페이스() {
        List<ReplaySegmentColor> c = PaceColorizer.colorize(
                List.of(seg(0, 330), seg(1, 330), seg(2, 330)), ColorParams.defaults());
        assertThat(c).extracting(ReplaySegmentColor::colorBucket).containsExactly(2, 2, 2);
    }

    @Test
    @DisplayName("빈 세그먼트 → 빈 색상 목록")
    void 빈_세그먼트() {
        assertThat(PaceColorizer.colorize(List.of(), ColorParams.defaults())).isEmpty();
    }

    @Test
    @DisplayName("커스텀 경계(파라미터 주입) — 하드코딩 아님을 증명")
    void 커스텀_경계_주입() {
        ColorParams custom = new ColorParams(new int[] {180, 360});   // 3버킷
        List<ReplaySegmentColor> c = PaceColorizer.colorize(
                List.of(seg(0, 100), seg(1, 180), seg(2, 360)), custom);
        // 100<180→0, 180은 <180 아님·<360→1, 360은 둘 다 초과→2
        assertThat(c).extracting(ReplaySegmentColor::colorBucket).containsExactly(0, 1, 2);
    }
}
