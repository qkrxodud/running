package com.runningcrew.race.domain;

import java.util.List;

/**
 * 경로 VO(순수 도메인) — 인코딩 폴리라인 문자열 + 디코딩 좌표열. 발행 후 불변.
 */
public record RoutePath(String encoded, List<LatLng> points) {

    public RoutePath {
        points = List.copyOf(points);
    }

    /** 인코딩 문자열에서 좌표열을 디코딩해 VO를 만든다(1e5 상호운용). */
    public static RoutePath fromEncoded(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new InvalidCourseException("route_polyline은 비어 있을 수 없습니다.");
        }
        return new RoutePath(encoded, PolylineCodec.decode(encoded));
    }
}
