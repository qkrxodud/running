package com.runningcrew.tracking.domain;

/**
 * 좌표 VO(순수 도메인 — ArchUnit R-1). 폴리라인 디코딩 결과를 손실 없이 담는다.
 *
 * <p>tracking 컨텍스트 로컬 표현 — race 컨텍스트의 {@code LatLng}를 참조하지 않는다(R-2: 컨텍스트 간
 * 클래스 의존 금지). 폴리라인 규약(1e5)은 계약(track-api.md §0)으로 동치가 보장된다.
 */
public record TrackCoord(double lat, double lng) {
}
