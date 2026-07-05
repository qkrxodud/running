package com.runningcrew.replay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.tracking.domain.TrackSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A3 색상 seed(골든 확장은 test-engineer). 페이스→버킷 경계(기본 [240,300,360,420]) 매핑·결정성.
 */
class PaceColorizerTest {

    @Test
    void 페이스_버킷_경계_매핑() {
        // 경계 [240,300,360,420]: <240→0, [240,300)→1, [300,360)→2, [360,420)→3, ≥420→4.
        List<TrackSegment> segments = List.of(
                new TrackSegment(0, 0, 500, 60, 200),    // 매우 빠름 → 0
                new TrackSegment(1, 500, 1000, 60, 240),  // 경계 정확히 240 → 1(< 아님)
                new TrackSegment(2, 1000, 1500, 60, 330), // → 2
                new TrackSegment(3, 1500, 1800, 45, 500)); // 느림 → 4(초과)

        List<ReplaySegmentColor> colored = PaceColorizer.colorize(segments, ColorParams.defaults());

        assertThat(colored).hasSize(4);
        assertThat(colored.get(0).colorBucket()).isEqualTo(0);
        assertThat(colored.get(1).colorBucket()).isEqualTo(1);   // 240은 버킷1(경계 등호 처리)
        assertThat(colored.get(2).colorBucket()).isEqualTo(2);
        assertThat(colored.get(3).colorBucket()).isEqualTo(4);
        assertThat(colored.get(0).segIndex()).isZero();
        assertThat(colored.get(3).endDistM()).isEqualTo(1800);   // 마지막 미완 구간 보존
    }
}
