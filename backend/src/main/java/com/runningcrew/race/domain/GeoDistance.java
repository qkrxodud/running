package com.runningcrew.race.domain;

import java.util.List;

/**
 * 하버사인 거리 누적(순수 함수 — 골든 대상). 코스 총거리(distance_m)를 등록 시 서버가 확정하는 데 쓴다.
 *
 * <p>정제 후 주행거리 비교(완주 판정)는 M2 FinishPolicy 소관이며, 여기 값은 코스 총거리 기준값이다.
 */
public final class GeoDistance {

    /** 지구 평균 반경(m). 임계값·거리 산정 튜닝 대상이나 기본은 표준 평균 반경. */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoDistance() {
    }

    /** 좌표열의 연속 구간 하버사인 거리 합(m). 2점 미만이면 0. */
    public static int totalMeters(List<LatLng> points) {
        double sum = 0.0;
        for (int i = 1; i < points.size(); i++) {
            sum += segmentMeters(points.get(i - 1), points.get(i));
        }
        return (int) Math.round(sum);
    }

    private static double segmentMeters(LatLng a, LatLng b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
