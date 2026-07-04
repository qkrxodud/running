package com.runningcrew.tracking.domain;

import java.util.List;

/**
 * 트랙 거리·근접 계산(순수 함수 — 골든 전제, ArchUnit R-1). tracking 로컬 유틸(race {@code GeoDistance}
 * 미참조 — R-2). 거리는 하버사인(R=6,371,000m), 점-폴리라인 근접은 국소 평면 근사(결정적).
 */
public final class TrackGeo {

    static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double M_PER_DEG_LAT = 111_320.0;

    private TrackGeo() {
    }

    /** 두 좌표 하버사인 거리(m). */
    public static double haversineMeters(TrackCoord a, TrackCoord b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** 좌표열 연속 구간 하버사인 누적(m, 반올림 int). 2점 미만 0. */
    public static int totalMeters(List<TrackCoord> coords) {
        double sum = 0.0;
        for (int i = 1; i < coords.size(); i++) {
            sum += haversineMeters(coords.get(i - 1), coords.get(i));
        }
        return (int) Math.round(sum);
    }

    /**
     * 점 P가 폴리라인(코스)까지의 최소 거리(m). 각 구간에 대한 점-선분 거리의 최소값.
     * 폴리라인이 1점뿐이면 그 점까지의 하버사인.
     */
    public static double distanceToPolyline(TrackCoord p, List<TrackCoord> polyline) {
        if (polyline.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (polyline.size() == 1) {
            return haversineMeters(p, polyline.get(0));
        }
        double min = Double.POSITIVE_INFINITY;
        for (int i = 1; i < polyline.size(); i++) {
            min = Math.min(min, pointToSegmentMeters(p, polyline.get(i - 1), polyline.get(i)));
        }
        return min;
    }

    /** 점-선분 거리(m) — P 기준 국소 평면(equirectangular) 근사. */
    private static double pointToSegmentMeters(TrackCoord p, TrackCoord a, TrackCoord b) {
        double mPerDegLng = M_PER_DEG_LAT * Math.cos(Math.toRadians(p.lat()));
        double px = 0.0;
        double py = 0.0;
        double ax = (a.lng() - p.lng()) * mPerDegLng;
        double ay = (a.lat() - p.lat()) * M_PER_DEG_LAT;
        double bx = (b.lng() - p.lng()) * mPerDegLng;
        double by = (b.lat() - p.lat()) * M_PER_DEG_LAT;
        double dx = bx - ax;
        double dy = by - ay;
        double segLenSq = dx * dx + dy * dy;
        if (segLenSq == 0.0) {
            return Math.hypot(ax - px, ay - py);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / segLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + t * dx;
        double cy = ay + t * dy;
        return Math.hypot(cx - px, cy - py);
    }
}
