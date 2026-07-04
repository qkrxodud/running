package com.runningcrew.tracking.domain;

import static com.runningcrew.tracking.domain.TrackTestFixtures.point;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TrackSegmentService} 경계 카탈로그 골든(설계 42 §5.1). 500m 경계 분할·마지막 미완 구간·
 * 2점 미만. 좌표는 자오선(정확 하버사인), 누적거리를 250m 배수로 두어 경계가 포인트에 정렬되게 한다.
 */
class TrackSegmentServiceGoldenTest {

    private static final SegmentParams P = SegmentParams.defaults();   // 500 m

    private static RefinedTrack track(List<TrackPoint> points) {
        return new RefinedTrack(points, TrackTestFixtures.specTotal(points), List.of());
    }

    @Test
    @DisplayName("2점 미만(1점)은 빈 구간 목록")
    void 단일_포인트_빈목록() {
        assertThat(TrackSegmentService.segments(track(List.of(point(0, 0, 0))), P)).isEmpty();
    }

    @Test
    @DisplayName("빈 트랙은 빈 구간 목록")
    void 빈_트랙_빈목록() {
        assertThat(TrackSegmentService.segments(track(List.of()), P)).isEmpty();
    }

    @Test
    @DisplayName("정확히 500m는 단일 구간 [0,500]")
    void 정확히_500m_단일구간() {
        List<TrackSegment> segs = TrackSegmentService.segments(track(List.of(
                point(0, 0, 0), point(250, 0, 150_000), point(500, 0, 300_000))), P);
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).index()).isZero();
        assertThat(segs.get(0).startDistanceM()).isZero();
        assertThat(segs.get(0).endDistanceM()).isEqualTo(500);
        assertThat(segs.get(0).durationS()).isEqualTo(300);          // 0 → 300s
        assertThat(segs.get(0).avgPaceSPerKm()).isEqualTo(600);      // 300s / 0.5km
    }

    // R-008 CLOSED(2026-07-04, backend-dev): TrackSegmentService에 상대 epsilon 도입으로 유령 0m 구간 제거.
    // @Disabled 제거 → green 상시 편입.
    @Test
    @DisplayName("R008: 정확히 1000m(구간길이 배수)는 두 완전 구간 — 유령 0m 구간 없음")
    void R008_정확히_배수거리는_유령_0m_구간을_만들지_않는다() {
        // 스펙(설계 42 §5.1): 500m 구간으로 1000m 주행 → 정확히 2구간 [0,500],[500,1000].
        // 버그: 내부 누적거리 하버사인이 1000.0000000001 로 미소 초과 → Math.ceil(total/500)=3 →
        // [1000,1000] 0m·0s 유령 구간 1개 추가. exact 500m 케이스는 FP 미달로 우연히 정상(1구간)이라,
        // "구간길이 배수" 처리가 FP 부호 의존적으로 비일관. 기대값은 구현 역산이 아니라 스펙에서 도출.
        List<TrackSegment> segs = TrackSegmentService.segments(track(List.of(
                point(0, 0, 0), point(250, 0, 150_000), point(500, 0, 300_000),
                point(750, 0, 450_000), point(1000, 0, 600_000))), P);
        assertThat(segs).hasSize(2);   // 현재 구현은 3 (유령 구간) → red
        assertThat(segs.get(1).startDistanceM()).isEqualTo(500);
        assertThat(segs.get(1).endDistanceM()).isEqualTo(1000);
    }

    @Test
    @DisplayName("마지막 미완 구간: 750m는 [0,500] 완전 + [500,750] 부분(250m)")
    void 마지막_미완_구간() {
        List<TrackSegment> segs = TrackSegmentService.segments(track(List.of(
                point(0, 0, 0), point(250, 0, 150_000),
                point(500, 0, 300_000), point(750, 0, 450_000))), P);
        assertThat(segs).hasSize(2);
        assertThat(segs.get(1).index()).isEqualTo(1);
        assertThat(segs.get(1).startDistanceM()).isEqualTo(500);
        assertThat(segs.get(1).endDistanceM()).isEqualTo(750);      // 총거리에서 잘림
        // 부분 구간 길이 250m, 300s→450s = 150s → 150/0.25 = 600 s/km
        assertThat(segs.get(1).durationS()).isEqualTo(150);
        assertThat(segs.get(1).avgPaceSPerKm()).isEqualTo(600);
    }
}
