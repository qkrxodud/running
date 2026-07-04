package com.runningcrew.race.domain;

/**
 * 좌표 VO(순수 도메인 — ArchUnit R-1). 범위 검증은 {@link Course#create}에서 수행하고
 * 이 record 자체는 값 보관만 한다(폴리라인 디코딩 결과를 손실 없이 담기 위함).
 */
public record LatLng(double lat, double lng) {
}
