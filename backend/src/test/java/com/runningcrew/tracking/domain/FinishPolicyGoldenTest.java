package com.runningcrew.tracking.domain;

import static com.runningcrew.tracking.domain.TrackTestFixtures.coord;
import static com.runningcrew.tracking.domain.TrackTestFixtures.point;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link FinishPolicy} 경계 카탈로그 골든(golden-testing 스킬 · 설계 42 §4). 3조건 AND, 임계 등호
 * (반경 ≤30m, 거리 ≥90%, 일치율 ≥80%/코리도 ≤50m), finished_at=도착 반경 <b>최초</b> 진입 시각.
 *
 * <p><b>설계</b>: {@link RefinedTrack} 를 직접 구성해 좌표·거리를 결정적으로 통제한다(정제·스무딩과 분리).
 * 코스는 자오선 직선 S(0m)→F(1000m). {@code totalDistanceM} 은 ②를 격리하기 위해 명시적으로 주입한다.
 * 기대값은 계획서 §4·설계 42 §4.1 에서 도출(구현 역산 아님).
 */
class FinishPolicyGoldenTest {

    private static final int COURSE_M = 1000;
    private static final TrackCoord FINISH = coord(1000, 0);
    // 자오선 코스 폴리라인(코리도 기준선)
    private static final CourseShape COURSE =
            new CourseShape(List.of(coord(0, 0), coord(1000, 0)), FINISH, COURSE_M);
    private static final FinishParams P = FinishParams.defaults();   // 30 / 0.90 / 0.80 / 50

    private static RefinedTrack track(int distanceM, List<TrackPoint> points) {
        return new RefinedTrack(points, distanceM, List.of());
    }

    // ---------- 3조건 모두 충족 ----------

    @Test
    @DisplayName("3조건 충족 → FINISHED, finished_at은 도착 반경 최초 진입 시각")
    void 모든_조건_충족_finishedAt_최초진입() {
        RefinedTrack r = track(950, List.of(
                point(0, 0, 0),
                point(500, 0, 100_000),
                point(970, 0, 190_000),      // 결승 30m 이내 최초 진입
                point(1000, 0, 200_000)));   // 결승선
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.FINISHED);
        assertThat(j.finishedAtMillis()).isEqualTo(190_000L);   // 최초 진입점(1000m 아님)
    }

    // ---------- 각 조건 단독 미충족(다른 둘 충족) → DNF ----------

    @Test
    @DisplayName("①단독 미충족(도착 미진입) — 거리·일치율 충족이어도 DNF")
    void 조건1_단독_미충족_도착_미진입_DNF() {
        RefinedTrack r = track(950, List.of(
                point(0, 0, 0),
                point(500, 0, 100_000),
                point(960, 0, 190_000)));   // 결승 40m — 반경 30m 밖
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.DNF);
        assertThat(j.finishedAtMillis()).isNull();
    }

    @Test
    @DisplayName("②단독 미충족(지름길 거리부족) — 도착·일치율 충족이어도 DNF")
    void 조건2_단독_미충족_지름길_DNF() {
        RefinedTrack r = track(899, List.of(      // 899 < 900(=90%)
                point(0, 0, 0),
                point(1000, 0, 200_000)));        // 도착 진입(①ok), 코리도 내(③ok)
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.DNF);
    }

    @Test
    @DisplayName("③단독 미충족(다른 길 일치율부족) — 도착·거리 충족이어도 DNF")
    void 조건3_단독_미충족_다른길_DNF() {
        RefinedTrack r = track(950, List.of(
                point(1000, 0, 200_000),   // 코리도 내 + 도착(①ok)
                point(200, 200, 20_000),   // 코스 200m 이탈(코리도 밖)
                point(400, 200, 40_000),
                point(600, 200, 60_000),
                point(800, 200, 80_000)));  // 5점 중 1점만 코리도 내 → 20% < 80%
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.DNF);
    }

    // ---------- ① 반경 경계(29.9 / 30 / 30.1) ----------

    @Test
    @DisplayName("반경 경계: 정확히 30m는 완주(≤ 포함)")
    void 반경_정확히_30m_완주() {
        RefinedTrack r = track(950, List.of(
                point(0, 0, 0), point(970, 0, 190_000)));   // 결승 30.0m
        assertThat(FinishPolicy.judge(r, COURSE, P).status()).isEqualTo(FinishStatus.FINISHED);
    }

    @Test
    @DisplayName("반경 경계: 29m(직전)은 완주")
    void 반경_29m_직전_완주() {
        RefinedTrack r = track(950, List.of(
                point(0, 0, 0), point(971, 0, 190_000)));   // 결승 29m
        assertThat(FinishPolicy.judge(r, COURSE, P).status()).isEqualTo(FinishStatus.FINISHED);
    }

    @Test
    @DisplayName("반경 경계: 31m(직후)은 DNF")
    void 반경_31m_직후_DNF() {
        RefinedTrack r = track(950, List.of(
                point(0, 0, 0), point(969, 0, 190_000)));   // 결승 31m — 반경 밖
        assertThat(FinishPolicy.judge(r, COURSE, P).status()).isEqualTo(FinishStatus.DNF);
    }

    // ---------- ② 거리 경계(89.9 / 90 / 90.1%) ----------

    @Test
    @DisplayName("거리 경계: 정확히 90%(900m)는 완주(≥ 포함)")
    void 거리_정확히_90퍼_완주() {
        RefinedTrack r = track(900, List.of(     // 900 == 1000 × 0.90
                point(0, 0, 0), point(1000, 0, 200_000)));
        assertThat(FinishPolicy.judge(r, COURSE, P).status()).isEqualTo(FinishStatus.FINISHED);
    }

    @Test
    @DisplayName("거리 경계: 899m(직전)은 DNF")
    void 거리_899_직전_DNF() {
        RefinedTrack r = track(899, List.of(
                point(0, 0, 0), point(1000, 0, 200_000)));
        assertThat(FinishPolicy.judge(r, COURSE, P).status()).isEqualTo(FinishStatus.DNF);
    }

    @Test
    @DisplayName("거리 경계: 901m(직후)은 완주")
    void 거리_901_직후_완주() {
        RefinedTrack r = track(901, List.of(
                point(0, 0, 0), point(1000, 0, 200_000)));
        assertThat(FinishPolicy.judge(r, COURSE, P).status()).isEqualTo(FinishStatus.FINISHED);
    }

    // ---------- ③ 일치율 경계(정확히 80% / 직전) ----------

    @Test
    @DisplayName("일치율 경계: 정확히 80%(5점 중 4점 코리도 내)는 완주(≥ 포함)")
    void 일치율_정확히_80퍼_완주() {
        RefinedTrack r = track(950, List.of(
                point(1000, 0, 200_000),   // 코리도 내 + 도착(①ok)
                point(250, 0, 25_000),     // 코리도 내
                point(500, 0, 50_000),     // 코리도 내
                point(750, 0, 75_000),     // 코리도 내
                point(600, 200, 60_000))); // 코리도 밖 → 4/5 = 0.80
        assertThat(FinishPolicy.judge(r, COURSE, P).status()).isEqualTo(FinishStatus.FINISHED);
    }

    @Test
    @DisplayName("일치율 경계: 80% 직전(9점 중 7점=0.777)은 DNF")
    void 일치율_80퍼_직전_DNF() {
        RefinedTrack r = track(950, List.of(
                point(1000, 0, 200_000),   // 도착 + 코리도 내
                point(125, 0, 12_500), point(250, 0, 25_000), point(375, 0, 37_500),
                point(500, 0, 50_000), point(625, 0, 62_500), point(750, 0, 75_000),  // 코리도 내 6점
                point(300, 200, 30_000), point(400, 200, 40_000)));   // 코리도 밖 2점 → 7/9 ≈ 0.777
        assertThat(FinishPolicy.judge(r, COURSE, P).status()).isEqualTo(FinishStatus.DNF);
    }

    // ---------- finished_at = 최초 진입(진입 후 계속 달림) ----------

    @Test
    @DisplayName("도착 진입 후 계속 달려 재진입해도 finished_at은 최초 진입 시각")
    void 진입후_계속달림_finishedAt은_최초() {
        RefinedTrack r = track(950, List.of(
                point(0, 0, 0),
                point(985, 0, 100_000),    // 결승 15m — 최초 진입(T=100_000)
                point(900, 0, 150_000),    // 결승 100m — 이탈
                point(1000, 0, 200_000)));  // 재진입(T=200_000)
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.FINISHED);
        assertThat(j.finishedAtMillis()).isEqualTo(100_000L);   // 재진입 아님
    }

    // ---------- DNF 기록·경로 보존 ----------

    @Test
    @DisplayName("DNF여도 정제 트랙·거리는 보존된다(판정은 트랙을 변경하지 않음)")
    void DNF여도_트랙_보존() {
        List<TrackPoint> points = List.of(point(0, 0, 0), point(960, 0, 190_000));
        RefinedTrack r = track(950, points);   // 도착 40m 밖 → DNF
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.DNF);
        // 리플레이 표시용 — 경로·거리 그대로
        assertThat(r.points()).isEqualTo(points);
        assertThat(r.totalDistanceM()).isEqualTo(950);
    }

    // ---------- 빈 트랙 / GPS 공백 ----------

    @Test
    @DisplayName("빈 트랙 → DNF(도착 미진입·일치율 0)")
    void 빈_트랙_DNF() {
        RefinedTrack r = track(0, List.of());
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.DNF);
        assertThat(j.finishedAtMillis()).isNull();
    }

    @Test
    @DisplayName("GPS 공백이 있어도 3조건 충족이면 완주(공백은 판정 미사용)")
    void GPS_공백있어도_완주() {
        RefinedTrack r = new RefinedTrack(
                List.of(point(0, 0, 0), point(500, 0, 100_000), point(1000, 0, 300_000)),
                950,
                List.of(new GpsGap(1, 2, 200_000L)));   // 공백 메타 존재
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.FINISHED);
    }

    // ---------- 픽스처(계약 shape) end-to-end ----------

    @Test
    @DisplayName("픽스처 완주 트랙: 정제→판정 FINISHED, finished_at 골든")
    void 픽스처_완주_판정() {
        RefinedTrack r = TrackRefinementService.refine(
                TrackTestFixtures.loadUpload("completed_run_lingers_at_finish.json"),
                RefinementParams.defaults());
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.FINISHED);
        assertThat(j.finishedAtMillis()).isEqualTo(1_752_181_530_000L);   // 픽스처 _meta
    }

    @Test
    @DisplayName("픽스처 지름길 트랙: DNF, finished_at null, 거리는 보존")
    void 픽스처_지름길_판정() {
        RefinedTrack r = TrackRefinementService.refine(
                TrackTestFixtures.loadUpload("shortcut_dnf_6pt.json"),
                RefinementParams.defaults());
        FinishJudgment j = FinishPolicy.judge(r, COURSE, P);
        assertThat(j.status()).isEqualTo(FinishStatus.DNF);
        assertThat(j.finishedAtMillis()).isNull();
        assertThat(r.totalDistanceM()).isEqualTo(400);   // 보존
    }
}
