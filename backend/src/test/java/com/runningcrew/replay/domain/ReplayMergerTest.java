package com.runningcrew.replay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.tracking.domain.GpsGap;
import com.runningcrew.tracking.domain.TrackCoord;
import com.runningcrew.tracking.domain.TrackSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A1 병합 seed(골든 확장은 test-engineer). t=0 상대시각 정렬·cum_dist(refined 하버사인)·is_gap·DNF
 * finish_time_ms null·duration_ms=전 참가자 최대 상대 종료.
 */
class ReplayMergerTest {

    @Test
    void t0_상대시각_정렬_cum_dist_is_gap_DNF_duration() {
        // 참가자 A: 시작 100000ms, 3점, 마지막에 gap(endIndex=2). 완주(finish 200000).
        ReplayTrackInput a = new ReplayTrackInput(7L, 100_000L, 200_000L, "FINISHED",
                List.of(new TrackCoord(37.5000, 127.0000), new TrackCoord(37.5000, 127.0009),
                        new TrackCoord(37.5000, 127.0018)),
                new long[] {100_000L, 130_000L, 190_000L},
                List.of(new GpsGap(1, 2, 60_000L)),
                List.of(new TrackSegment(0, 0, 160, 30, 300)));
        // 참가자 B: 시작 105000ms(늦게 출발), 2점, DNF(finishedAt null).
        ReplayTrackInput b = new ReplayTrackInput(3L, 105_000L, null, "DNF",
                List.of(new TrackCoord(37.5000, 127.0000), new TrackCoord(37.5000, 127.0009)),
                new long[] {105_000L, 140_000L}, List.of(), List.of());

        MergedTimeline merged = ReplayMerger.mergeToRelativeTimeline(List.of(a, b),
                ColorParams.defaults());

        ReplayParticipant pa = merged.participants().get(0);
        assertThat(pa.userId()).isEqualTo(7L);
        assertThat(pa.frames().get(0).tMs()).isZero();               // 각자 t=0
        assertThat(pa.frames().get(1).tMs()).isEqualTo(30_000L);     // 130000-100000
        assertThat(pa.frames().get(0).cumDistM()).isZero();
        assertThat(pa.frames().get(2).cumDistM()).isGreaterThan(0);  // refined 하버사인 누적
        assertThat(pa.frames().get(2).isGap()).isTrue();             // gap endIndex=2
        assertThat(pa.frames().get(1).isGap()).isFalse();
        assertThat(pa.finishTimeMs()).isEqualTo(100_000L);           // 200000-100000

        ReplayParticipant pb = merged.participants().get(1);
        assertThat(pb.frames().get(0).tMs()).isZero();               // 늦게 출발해도 t=0
        assertThat(pb.finishTimeMs()).isNull();                      // DNF(RP-6)

        // duration_ms = 전 참가자 최대 상대 종료(A finish 100000 vs B 마지막 프레임 35000) = 100000
        assertThat(merged.durationMs()).isEqualTo(100_000L);
    }
}
