package com.runningcrew.tracking.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * GPS 트랙 정제 — <b>순수 함수</b>(IO·시계·랜덤 0, ArchUnit R-1, 골든 대상 A4). 파이프라인 순서 고정
 * (설계 42 §3.1):
 *
 * <ol>
 *   <li>accuracy 임계 필터: {@code accuracy > accuracyMaxM} 제거.
 *   <li>이상 점프 보정: 직전 유효점 대비 순간속도 {@code > maxSpeedMps} 제거(Δt≤0은 판정 생략).
 *   <li>이동평균 스무딩: 창 {@code smoothingWindow}(홀수)로 좌표만 평활(시각·속도·정확도 원본 유지).
 *   <li>정제 후 거리: 정제 좌표열 하버사인 누적(FR-1 — 원시 하버사인 금지).
 *   <li>GPS 공백 식별: 인접 유효 포인트 Δt {@code > gapThresholdS} → gap 메타(FR-3).
 * </ol>
 *
 * <p><b>그로스 타임 불변(FR-2)</b>: 정제는 정지 구간을 삭제·보정하지 않는다 — 신호대기 포함 실경과 유지.
 * accuracy/점프 필터는 노이즈 제거지 정지 제거가 아니다.
 */
public final class TrackRefinementService {

    private TrackRefinementService() {
    }

    public static RefinedTrack refine(List<TrackPoint> raw, RefinementParams params) {
        List<TrackPoint> accepted = new ArrayList<>();

        // 1) accuracy 임계 + 2) 이상 점프(직전 유효점 대비 순간속도) — 한 번의 순회로 순서 보존.
        for (TrackPoint p : raw) {
            if (p.accuracy() > params.accuracyMaxM()) {
                continue;   // 1) accuracy 초과 제거
            }
            if (!accepted.isEmpty()) {
                TrackPoint prev = accepted.get(accepted.size() - 1);
                long dtMs = p.tsMillis() - prev.tsMillis();
                if (dtMs > 0) {
                    double dist = TrackGeo.haversineMeters(prev.coord(), p.coord());
                    double speed = dist / (dtMs / 1000.0);
                    if (speed > params.maxSpeedMps()) {
                        continue;   // 2) 순간이동(이상 점프) 제거
                    }
                }
            }
            accepted.add(p);
        }

        // 3) 이동평균 스무딩(좌표만)
        List<TrackPoint> smoothed = smooth(accepted, params.smoothingWindow());

        // 4) 정제 후 거리
        int totalDistanceM = TrackGeo.totalMeters(smoothed.stream().map(TrackPoint::coord).toList());

        // 5) GPS 공백
        List<GpsGap> gaps = findGaps(smoothed, params.gapThresholdS());

        return new RefinedTrack(smoothed, totalDistanceM, gaps);
    }

    private static List<TrackPoint> smooth(List<TrackPoint> points, int window) {
        int w = Math.max(1, window);
        if (w % 2 == 0) {
            w += 1;   // 홀수 강제
        }
        int half = w / 2;
        if (half == 0 || points.size() < 2) {
            return List.copyOf(points);
        }
        List<TrackPoint> out = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            int from = Math.max(0, i - half);
            int to = Math.min(points.size() - 1, i + half);
            double sumLat = 0.0;
            double sumLng = 0.0;
            int n = 0;
            for (int j = from; j <= to; j++) {
                sumLat += points.get(j).lat();
                sumLng += points.get(j).lng();
                n++;
            }
            TrackPoint c = points.get(i);
            out.add(new TrackPoint(sumLat / n, sumLng / n, c.tsMillis(), c.speed(), c.accuracy(),
                    c.altitude()));
        }
        return out;
    }

    private static List<GpsGap> findGaps(List<TrackPoint> points, int gapThresholdS) {
        long thresholdMs = (long) gapThresholdS * 1000L;
        List<GpsGap> gaps = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            long dt = points.get(i).tsMillis() - points.get(i - 1).tsMillis();
            if (dt > thresholdMs) {
                gaps.add(new GpsGap(i - 1, i, dt));
            }
        }
        return gaps;
    }
}
