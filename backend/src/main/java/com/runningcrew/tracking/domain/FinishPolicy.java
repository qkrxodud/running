package com.runningcrew.tracking.domain;

import java.util.List;

/**
 * 완주/DNF 판정 — <b>순수 함수</b>(ArchUnit R-1, 골든 대상 A6). <b>코스 이탈 검증 일원화 지점</b>(FP-4 —
 * 업로드 서비스에 중복 구현 0). 3조건 전부 AND → FINISHED, 하나라도 미충족 → DNF(기록·경로 보존).
 *
 * <ul>
 *   <li>① 도착 반경: 정제 포인트 중 finish 좌표로부터 {@code ≤ radiusM}인 것 존재.
 *   <li>② 거리: {@code 정제거리 ≥ course.distanceM × minDistanceRatio}.
 *   <li>③ 코스 일치율: 정제 포인트의 {@code ≥ coverageRatio} 비율이 코스 폴리라인으로부터 {@code ≤ corridorM}.
 * </ul>
 *
 * <p>finished_at = ①을 만족하는 <b>최초</b> 정제 포인트의 GPS 시각(FP-2). 종료 버튼 시각 불신. DNF면 null.
 */
public final class FinishPolicy {

    private FinishPolicy() {
    }

    public static FinishJudgment judge(RefinedTrack refined, CourseShape course, FinishParams params) {
        List<TrackPoint> points = refined.points();

        // ① 도착 반경 최초 진입
        Long firstEntryMillis = null;
        for (TrackPoint p : points) {
            if (TrackGeo.haversineMeters(p.coord(), course.finish()) <= params.radiusM()) {
                firstEntryMillis = p.tsMillis();
                break;
            }
        }
        boolean cond1 = firstEntryMillis != null;

        // ② 거리
        boolean cond2 = refined.totalDistanceM()
                >= course.distanceM() * params.minDistanceRatio();

        // ③ 코스 일치율
        boolean cond3 = coverageRatio(points, course.polyline(), params.corridorM())
                >= params.coverageRatio();

        if (cond1 && cond2 && cond3) {
            return new FinishJudgment(FinishStatus.FINISHED, firstEntryMillis);
        }
        return new FinishJudgment(FinishStatus.DNF, null);
    }

    private static double coverageRatio(List<TrackPoint> points, List<TrackCoord> polyline,
                                        double corridorM) {
        if (points.isEmpty()) {
            return 0.0;
        }
        int within = 0;
        for (TrackPoint p : points) {
            if (TrackGeo.distanceToPolyline(p.coord(), polyline) <= corridorM) {
                within++;
            }
        }
        return (double) within / points.size();
    }
}
