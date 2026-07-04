package com.runningcrew.tracking.domain;

import java.util.List;

/**
 * 정제 결과(순수). 정제 포인트열 + <b>정제 후 좌표로 계산한 거리</b>(FR-1: 원시 하버사인 금지) + GPS 공백 메타.
 *
 * @param points         정제 포인트열(원시 대비 필터·스무딩 적용)
 * @param totalDistanceM 정제 후 좌표 하버사인 누적(m)
 * @param gaps           GPS 유실 구간(개수는 결과 응답 gps_gap_count)
 */
public record RefinedTrack(List<TrackPoint> points, int totalDistanceM, List<GpsGap> gaps) {

    public int gapCount() {
        return gaps.size();
    }

    public List<TrackCoord> coords() {
        return points.stream().map(TrackPoint::coord).toList();
    }
}
