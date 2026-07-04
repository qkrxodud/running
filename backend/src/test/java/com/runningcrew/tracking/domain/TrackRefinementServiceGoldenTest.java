package com.runningcrew.tracking.domain;

import static com.runningcrew.tracking.domain.TrackTestFixtures.point;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TrackRefinementService} 경계 카탈로그 골든(golden-testing 스킬 · 설계 42 §3). seed(백엔드 통합)
 * 이 커버 못 한 경계만 추가한다. 기대값은 파이프라인 스펙(§3.1)·하버사인 공식에서 도출(구현 역산 금지).
 *
 * <p>임계값은 <b>파라미터 주입</b>(FP-3). 경계를 격리하려는 테스트는 {@code smoothingWindow=1}(스무딩 off)
 * 로 필터·공백 로직만 본다 — 스무딩 자체는 "정제 후 거리 &lt; 원시" 테스트에서 기본 창(3)으로 검증한다.
 */
class TrackRefinementServiceGoldenTest {

    /** 스무딩 off(창1) — accuracy/점프/공백 로직 격리용. */
    private static final RefinementParams NO_SMOOTH = new RefinementParams(50.0, 12.0, 1, 30);

    @Test
    @DisplayName("accuracy 임계(50) 초과 포인트는 제거된다")
    void accuracy_초과_제거() {
        List<TrackPoint> raw = List.of(
                point(0, 0, 0, 8.0),
                point(100, 0, 30_000, 99.0),    // accuracy 99 > 50 → 제거
                point(200, 0, 60_000, 8.0),
                point(300, 0, 90_000, 8.0));
        RefinedTrack r = TrackRefinementService.refine(raw, NO_SMOOTH);
        assertThat(r.points()).hasSize(3);
        assertThat(r.points()).allSatisfy(p -> assertThat(p.accuracy()).isLessThanOrEqualTo(50.0));
        // 남은 3점(자오선 0→200→300m): 200 + 100 = 300 m
        assertThat(r.totalDistanceM()).isEqualTo(300);
    }

    @Test
    @DisplayName("accuracy 정확히 50은 유지(임계는 초과일 때만 제거)")
    void accuracy_등호_50은_유지() {
        List<TrackPoint> raw = List.of(
                point(0, 0, 0, 50.0),
                point(100, 0, 30_000, 50.0));
        RefinedTrack r = TrackRefinementService.refine(raw, NO_SMOOTH);
        assertThat(r.points()).hasSize(2);   // 50 > 50 == false → 유지
    }

    @Test
    @DisplayName("순간이동(속도>12m/s) 점프는 제거된다")
    void 순간이동_점프_제거() {
        List<TrackPoint> raw = List.of(
                point(0, 0, 0),
                point(100, 0, 30_000),
                point(5000, 0, 60_000),   // 직전 대비 4900m/30s ≈ 163m/s > 12 → 제거
                point(200, 0, 90_000));
        RefinedTrack r = TrackRefinementService.refine(raw, NO_SMOOTH);
        assertThat(r.points()).hasSize(3);
        // 점프점(약 북 5000m) 부재 확인
        assertThat(r.points()).allSatisfy(p ->
                assertThat(p.lat()).isLessThan(TrackTestFixtures.lat(1000)));
    }

    @Test
    @DisplayName("정지 구간(동일 좌표 연속)은 삭제하지 않는다 — 그로스 타임(FR-2)")
    void 정지구간_미삭제_그로스타임() {
        List<TrackPoint> raw = List.of(
                point(0, 0, 0),
                point(0, 0, 30_000),     // 정지(동일 좌표)
                point(0, 0, 60_000),     // 정지
                point(100, 0, 90_000));
        RefinedTrack r = TrackRefinementService.refine(raw, NO_SMOOTH);
        assertThat(r.points()).hasSize(4);   // 정지점 보존(삭제 금지)
        assertThat(r.totalDistanceM()).isEqualTo(100);   // 정지 구간 0 m + 마지막 100 m
    }

    @Test
    @DisplayName("정제 후 거리는 원시 하버사인보다 작다(FR-1) — 직선 11점 창3: 1000→900 m")
    void 정제후_거리는_원시보다_작다() {
        List<TrackPoint> raw = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            raw.add(point(100 * i, 0, i * 30_000L));   // 100 m 간격 자오선 직선(원시 1000 m)
        }
        int rawTotal = TrackTestFixtures.specTotal(raw);
        RefinedTrack r = TrackRefinementService.refine(raw, RefinementParams.defaults());
        // 창3 이동평균: 양끝점이 이웃쪽으로 반칸(50m)씩 수축 → 총거리 정확히 한 칸(100m) 감소.
        assertThat(rawTotal).isEqualTo(1000);
        assertThat(r.totalDistanceM()).isEqualTo(900);
        assertThat(r.totalDistanceM()).isLessThan(rawTotal);
    }

    @Test
    @DisplayName("빈 트랙 → 빈 정제·거리 0·공백 없음")
    void 빈_트랙() {
        RefinedTrack r = TrackRefinementService.refine(List.of(), RefinementParams.defaults());
        assertThat(r.points()).isEmpty();
        assertThat(r.totalDistanceM()).isZero();
        assertThat(r.gaps()).isEmpty();
    }

    @Test
    @DisplayName("포인트 1개 → 1점 유지·거리 0")
    void 단일_포인트() {
        RefinedTrack r = TrackRefinementService.refine(
                List.of(point(0, 0, 0)), RefinementParams.defaults());
        assertThat(r.points()).hasSize(1);
        assertThat(r.totalDistanceM()).isZero();
    }

    @Test
    @DisplayName("전 구간 저품질(모두 accuracy>50) → 전부 제거되어 빈 트랙")
    void 전점_저품질() {
        List<TrackPoint> raw = List.of(
                point(0, 0, 0, 80.0),
                point(100, 0, 30_000, 80.0),
                point(200, 0, 60_000, 80.0));
        RefinedTrack r = TrackRefinementService.refine(raw, RefinementParams.defaults());
        assertThat(r.points()).isEmpty();
        assertThat(r.totalDistanceM()).isZero();
    }

    @Test
    @DisplayName("원시 입력 불변성 — refine 은 입력 리스트를 변경하지 않는다")
    void 원시_불변성() {
        List<TrackPoint> raw = new java.util.ArrayList<>(List.of(
                point(0, 0, 0),
                point(100, 0, 30_000, 99.0),   // 정제에서 걸러질 점 포함
                point(200, 0, 60_000)));
        List<TrackPoint> snapshot = List.copyOf(raw);
        TrackRefinementService.refine(raw, RefinementParams.defaults());
        assertThat(raw).isEqualTo(snapshot);   // 원시 보존(정제본은 별도 생성)
    }

    @Test
    @DisplayName("GPS 공백 임계(30s) 경계: 정확히 30s는 공백 아님, 30.001s는 공백")
    void 공백_임계_경계_30초() {
        // Δt = 30_000 ms 정확 → dt > 30_000 == false → 공백 아님
        RefinedTrack exact = TrackRefinementService.refine(List.of(
                point(0, 0, 0), point(50, 0, 30_000)), NO_SMOOTH);
        assertThat(exact.gapCount()).isZero();

        // Δt = 30_001 ms → 공백 1
        RefinedTrack over = TrackRefinementService.refine(List.of(
                point(0, 0, 0), point(50, 0, 30_001)), NO_SMOOTH);
        assertThat(over.gapCount()).isEqualTo(1);

        // Δt = 29_999 ms → 공백 아님
        RefinedTrack under = TrackRefinementService.refine(List.of(
                point(0, 0, 0), point(50, 0, 29_999)), NO_SMOOTH);
        assertThat(under.gapCount()).isZero();
    }

    @Test
    @DisplayName("합성 픽스처(계약 shape) 로드 → 완주 트랙 정제 골든: 951 m·공백 0")
    void 픽스처_완주_트랙_정제() {
        List<TrackPoint> raw = TrackTestFixtures.loadUpload("completed_run_lingers_at_finish.json");
        RefinedTrack r = TrackRefinementService.refine(raw, RefinementParams.defaults());
        assertThat(r.totalDistanceM()).isEqualTo(951);   // 픽스처 _meta 박제값
        assertThat(r.gapCount()).isZero();
    }

    @Test
    @DisplayName("합성 픽스처: 70s 공백 트랙 → 공백 1개 식별")
    void 픽스처_공백_트랙_정제() {
        List<TrackPoint> raw = TrackTestFixtures.loadUpload("gap_run_completed.json");
        RefinedTrack r = TrackRefinementService.refine(raw, RefinementParams.defaults());
        assertThat(r.totalDistanceM()).isEqualTo(951);
        assertThat(r.gapCount()).isEqualTo(1);
    }
}
