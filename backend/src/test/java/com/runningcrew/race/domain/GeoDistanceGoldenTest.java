package com.runningcrew.race.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link GeoDistance} 하버사인 누적 <b>골든</b>(코스 총거리 distance_m 서버 확정 경로).
 *
 * <p>기대값은 구면 하버사인 공식과 {@code R = 6_371_000 m}에서 <b>손계산·공식 도출</b>한다
 * (구현 실행 역산 아님). 대표 골든:
 * <ul>
 *   <li>적도 1° = R·(π/180) = 6371000·0.0174532925 ≈ 111194.93 m → 111195</li>
 *   <li>자오선 90°(적도→북극) = R·(π/2) ≈ 10007543 m</li>
 *   <li>반적도 180° = R·π ≈ 20015087 m</li>
 * </ul>
 * 임계값·정제 후 주행거리 비교(완주 판정)는 M2 FinishPolicy 소관이며 여기 값은 코스 총거리 기준값이다.
 */
class GeoDistanceGoldenTest {

    @Test
    @DisplayName("2점 미만은 거리 0 (빈 트랙·단일 점 경계)")
    void 점이_부족하면_0() {
        assertThat(GeoDistance.totalMeters(List.of())).isZero();
        assertThat(GeoDistance.totalMeters(List.of(new LatLng(37.5, 127.0)))).isZero();
    }

    @Test
    @DisplayName("동일 좌표 연속(정지 구간)은 0 m")
    void 동일_좌표는_0() {
        assertThat(GeoDistance.totalMeters(List.of(
                new LatLng(37.5, 127.0), new LatLng(37.5, 127.0))))
                .isZero();
    }

    @Test
    @DisplayName("적도 위도 1° = R·π/180 ≈ 111195 m")
    void 적도_1도_위도() {
        assertThat(GeoDistance.totalMeters(List.of(
                new LatLng(0.0, 0.0), new LatLng(1.0, 0.0))))
                .isEqualTo(111195);
    }

    @Test
    @DisplayName("적도 경도 1° 도 111195 m (적도에서 위·경도 대칭)")
    void 적도_1도_경도() {
        assertThat(GeoDistance.totalMeters(List.of(
                new LatLng(0.0, 0.0), new LatLng(0.0, 1.0))))
                .isEqualTo(111195);
    }

    @Test
    @DisplayName("자오선 90°(적도→북극) = R·π/2 ≈ 10007543 m")
    void 자오선_사분() {
        assertThat(GeoDistance.totalMeters(List.of(
                new LatLng(0.0, 0.0), new LatLng(90.0, 0.0))))
                .isEqualTo(10007543);
    }

    @Test
    @DisplayName("반적도 180° = R·π ≈ 20015087 m (대원 절반)")
    void 반적도() {
        assertThat(GeoDistance.totalMeters(List.of(
                new LatLng(0.0, 0.0), new LatLng(0.0, 180.0))))
                .isEqualTo(20015087);
    }

    @Test
    @DisplayName("위도 37.5°에서 경도 1°는 위도수축으로 88216 m (cos 37.5 반영)")
    void 중위도_경도_1도는_수축() {
        assertThat(GeoDistance.totalMeters(List.of(
                new LatLng(37.5, 127.0), new LatLng(37.5, 128.0))))
                .isEqualTo(88216);
    }

    @Test
    @DisplayName("course-api 예제 start→finish 구간 하버사인 = 2567 m")
    void 계약_예제_출발_도착() {
        assertThat(GeoDistance.totalMeters(List.of(
                new LatLng(37.5121, 127.0018), new LatLng(37.5288, 127.0219))))
                .isEqualTo(2567);
    }

    @Test
    @DisplayName("서울 근사 3점 누적 = 1448 m (다구간 합산)")
    void 다점_누적() {
        assertThat(GeoDistance.totalMeters(List.of(
                new LatLng(37.51235, 126.99640),
                new LatLng(37.51188, 127.00412),
                new LatLng(37.51602, 127.01105))))
                .isEqualTo(1448);
    }
}
