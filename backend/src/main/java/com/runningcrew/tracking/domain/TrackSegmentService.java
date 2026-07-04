package com.runningcrew.tracking.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 구간 페이스 요약 — <b>순수 함수</b>(ArchUnit R-1, 골든 대상 A5). 정제 트랙을 누적거리
 * {@code lengthM} 경계로 분할해 각 구간 소요·평균페이스를 계산한다. 등호 경계(정확히 500m 지점)와
 * 마지막 미완 구간을 결정적으로 처리한다.
 */
public final class TrackSegmentService {

    /**
     * 구간 개수 산정용 상대 epsilon(R-008). 하버사인 누적의 부동소수 미소 오차(≈1e-8 상대)가 구간 길이의
     * 정확한 배수를 아주 살짝 초과시켜 {@code Math.ceil}이 유령 0m 구간을 1개 더 만드는 것을 막는다.
     * ratio 기준 1e-6(= 500m 구간에서 0.5mm)은 실주행 경계를 잠식하지 않으면서 배수 오차를 흡수한다.
     */
    private static final double SEGMENT_COUNT_EPSILON = 1e-6;

    private TrackSegmentService() {
    }

    public static List<TrackSegment> segments(RefinedTrack refined, SegmentParams params) {
        List<TrackPoint> points = refined.points();
        List<TrackSegment> result = new ArrayList<>();
        if (points.size() < 2) {
            return result;
        }
        int lengthM = Math.max(1, params.lengthM());

        // 누적거리·시각 배열
        double[] cum = new double[points.size()];
        long[] ts = new long[points.size()];
        cum[0] = 0.0;
        ts[0] = points.get(0).tsMillis();
        for (int i = 1; i < points.size(); i++) {
            cum[i] = cum[i - 1]
                    + TrackGeo.haversineMeters(points.get(i - 1).coord(), points.get(i).coord());
            ts[i] = points.get(i).tsMillis();
        }
        double total = cum[cum.length - 1];
        if (total <= 0.0) {
            return result;
        }

        // 배수 경계의 FP 미소 초과를 epsilon으로 흡수 — total>0이므로 최소 1구간 보장.
        int segmentCount = Math.max(1, (int) Math.ceil(total / lengthM - SEGMENT_COUNT_EPSILON));
        for (int j = 0; j < segmentCount; j++) {
            double startD = (double) j * lengthM;
            double endD = Math.min((double) (j + 1) * lengthM, total);
            long startT = timeAtDistance(cum, ts, startD);
            long endT = timeAtDistance(cum, ts, endD);
            long durationS = Math.round((endT - startT) / 1000.0);
            double segLenM = endD - startD;
            int avgPace = segLenM > 0
                    ? (int) Math.round(durationS / (segLenM / 1000.0))
                    : 0;
            result.add(new TrackSegment(j, (int) Math.round(startD), (int) Math.round(endD),
                    durationS, avgPace));
        }
        return result;
    }

    /** 누적거리 D 지점의 시각(ms) — 구간 내 선형 보간. */
    private static long timeAtDistance(double[] cum, long[] ts, double d) {
        if (d <= cum[0]) {
            return ts[0];
        }
        int last = cum.length - 1;
        if (d >= cum[last]) {
            return ts[last];
        }
        for (int i = 1; i < cum.length; i++) {
            if (d <= cum[i]) {
                double span = cum[i] - cum[i - 1];
                if (span <= 0.0) {
                    return ts[i];
                }
                double f = (d - cum[i - 1]) / span;
                return Math.round(ts[i - 1] + (ts[i] - ts[i - 1]) * f);
            }
        }
        return ts[last];
    }
}
