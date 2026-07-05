package com.runningcrew.replay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.tracking.domain.GpsGap;
import com.runningcrew.tracking.domain.TrackCoord;
import com.runningcrew.tracking.domain.TrackSegment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ReplayMerger} 경계 카탈로그 골든(설계 72 §3.1). seed({@code ReplayMergerTest}: 2인 t=0 정렬·
 * cum_dist·is_gap·DNF finish null·duration)가 커버 못 한 <b>갭만</b> 추가. 기대값은 설계 72에서 도출.
 *
 * <p>좌표는 <b>자오선</b>(경도 고정, 위도만 변화)이라 하버사인 = {@code R·Δlat} 정확 —
 * cum_dist가 refined 기반(RP-4)임을 손계산 값으로 박제한다.
 */
class ReplayMergerGoldenTest {

    private static final double MER = 6_371_000.0 * Math.PI / 180.0;   // 자오선 m/도

    /** 북쪽 northM m 지점 좌표(자오선 — 하버사인 정확). */
    private static TrackCoord north(double northM) {
        return new TrackCoord(37.5 + northM / MER, 127.0);
    }

    private static ReplayTrackInput track(long userId, long startedAt, Long finishedAt,
                                          String status, List<TrackCoord> coords, long[] times,
                                          List<GpsGap> gaps) {
        return new ReplayTrackInput(userId, startedAt, finishedAt, status, coords, times, gaps,
                List.of(new TrackSegment(0, 0, 500, 100, 300)));
    }

    @Test
    @DisplayName("단일 참가자 — 1인 병합, duration_ms = 그 참가자 완주 상대시각")
    void 단일_참가자() {
        ReplayTrackInput a = track(7L, 100_000L, 150_000L, "FINISHED",
                List.of(north(0), north(100), north(200)),
                new long[] {100_000L, 120_000L, 150_000L}, List.of());
        MergedTimeline m = ReplayMerger.mergeToRelativeTimeline(List.of(a), ColorParams.defaults());
        assertThat(m.participants()).hasSize(1);
        assertThat(m.participants().get(0).finishTimeMs()).isEqualTo(50_000L);   // 150000-100000
        assertThat(m.durationMs()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("DNF가 가장 긴 트랙이면 duration_ms = DNF 마지막 프레임 t_ms(트랙 끝)")
    void DNF가_가장_길면_그_끝이_duration() {
        // A 완주(상대 종료 50000), B는 DNF지만 마지막 프레임 t_ms 80000 → duration=80000.
        ReplayTrackInput a = track(7L, 100_000L, 150_000L, "FINISHED",
                List.of(north(0), north(100)), new long[] {100_000L, 150_000L}, List.of());
        ReplayTrackInput bDnf = track(3L, 100_000L, null, "DNF",
                List.of(north(0), north(100), north(200)),
                new long[] {100_000L, 140_000L, 180_000L}, List.of());
        MergedTimeline m = ReplayMerger.mergeToRelativeTimeline(List.of(a, bDnf),
                ColorParams.defaults());
        assertThat(m.participants().get(1).finishTimeMs()).isNull();   // DNF
        assertThat(m.durationMs()).isEqualTo(80_000L);                 // DNF 마지막 프레임(180000-100000)
    }

    @Test
    @DisplayName("복수 GPS 공백 — 각 gap endIndex 프레임만 is_gap=true")
    void 복수_공백_endIndex_표기() {
        ReplayTrackInput a = track(7L, 0L, 100_000L, "FINISHED",
                List.of(north(0), north(100), north(200), north(300), north(400)),
                new long[] {0L, 10_000L, 60_000L, 70_000L, 130_000L},
                List.of(new GpsGap(1, 2, 50_000L), new GpsGap(3, 4, 60_000L)));
        List<ReplayFrame> frames = ReplayMerger.mergeToRelativeTimeline(List.of(a),
                ColorParams.defaults()).participants().get(0).frames();
        assertThat(frames).extracting(ReplayFrame::isGap)
                .containsExactly(false, false, true, false, true);   // endIndex 2·4만
    }

    @Test
    @DisplayName("공백 없으면 전 프레임 is_gap=false")
    void 공백_없으면_전부_false() {
        ReplayTrackInput a = track(7L, 0L, 30_000L, "FINISHED",
                List.of(north(0), north(100), north(200)),
                new long[] {0L, 10_000L, 30_000L}, List.of());
        assertThat(ReplayMerger.mergeToRelativeTimeline(List.of(a), ColorParams.defaults())
                .participants().get(0).frames())
                .extracting(ReplayFrame::isGap).containsExactly(false, false, false);
    }

    @Test
    @DisplayName("cum_dist는 refined 좌표 하버사인 누적(원시 아님, RP-4) — 자오선 100m 간격 손계산")
    void cum_dist는_refined_하버사인() {
        ReplayTrackInput a = track(7L, 0L, 40_000L, "FINISHED",
                List.of(north(0), north(100), north(300)),   // 0 → 100 → 300 m
                new long[] {0L, 20_000L, 40_000L}, List.of());
        List<ReplayFrame> frames = ReplayMerger.mergeToRelativeTimeline(List.of(a),
                ColorParams.defaults()).participants().get(0).frames();
        assertThat(frames.get(0).cumDistM()).isZero();
        assertThat(frames.get(1).cumDistM()).isEqualTo(100);   // 자오선 100m 정확
        assertThat(frames.get(2).cumDistM()).isEqualTo(300);   // +200m
    }

    @Test
    @DisplayName("빈 frames 참가자(좌표 없음) — 크래시 없이 빈 프레임, DNF finish null")
    void 빈_frames_참가자() {
        ReplayTrackInput empty = track(9L, 0L, null, "DNF", List.of(), new long[] {}, List.of());
        MergedTimeline m = ReplayMerger.mergeToRelativeTimeline(List.of(empty),
                ColorParams.defaults());
        ReplayParticipant p = m.participants().get(0);
        assertThat(p.frames()).isEmpty();
        assertThat(p.finishTimeMs()).isNull();
        assertThat(m.durationMs()).isZero();
    }
}
