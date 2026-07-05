package com.runningcrew.replay.domain;

import com.runningcrew.tracking.domain.TrackSegment;
import java.util.ArrayList;
import java.util.List;

/**
 * 페이스 색상 버킷(순수 함수 3종 中 A3). TrackSegment(500m, M2 재사용) → 색상 구간. IO·시계·랜덤 0(골든 대상).
 */
public final class PaceColorizer {

    private PaceColorizer() {
    }

    /** 구간별 페이스를 {@link ColorParams} 경계로 버킷화. 결정적 — 동일 입력 동일 출력. */
    public static List<ReplaySegmentColor> colorize(List<TrackSegment> segments, ColorParams params) {
        List<ReplaySegmentColor> result = new ArrayList<>(segments.size());
        for (TrackSegment s : segments) {
            result.add(new ReplaySegmentColor(s.index(), s.startDistanceM(), s.endDistanceM(),
                    s.avgPaceSPerKm(), params.bucketFor(s.avgPaceSPerKm())));
        }
        return result;
    }
}
