package com.runningcrew.tracking.domain;

/**
 * 트랙 포인트(순수 도메인 — ArchUnit R-1). 폴리라인 좌표 + 병렬 배열(GPS 시각·속도·정확도·고도)을 zip한 값.
 *
 * @param lat        위도
 * @param lng        경도
 * @param tsMillis   GPS 시각(epoch milliseconds, UTC — 계약 track-api §0). 기기 시계 아님.
 * @param speed      순간 속도(m/s)
 * @param accuracy   수평 정확도(m) — 정제 시 임계 필터 입력
 * @param altitude   고도(m, nullable) — 판정 미사용
 */
public record TrackPoint(double lat, double lng, long tsMillis, double speed, double accuracy,
                         Double altitude) {

    public TrackCoord coord() {
        return new TrackCoord(lat, lng);
    }
}
