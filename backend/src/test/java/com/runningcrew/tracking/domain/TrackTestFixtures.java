package com.runningcrew.tracking.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 골든 테스트 지오메트리·픽스처 로더(테스트 전용).
 *
 * <p><b>좌표 규약(손계산 정확성)</b>: 기준점 (37.5, 127.0). 북쪽 이동만 쓰면 좌표가 <b>자오선</b>(경도 고정)
 * 위에 놓여 하버사인 거리 = {@code R·Δlat} 로 <b>정확</b>하다({@link TrackGeo#haversineMeters} 와 동일 공식).
 * 따라서 {@code point(northM, …)} 는 도착 반경·정제 거리 기대값을 결정적으로 통제한다.
 *
 * <p>동쪽 이동({@code eastM})은 코스 이탈(코리도) 테스트용 — {@code TrackGeo} 의 점-폴리라인 근사
 * (등거리 평면, {@code MPDLAT=111320}) 와 정합하는 변환을 쓴다.
 *
 * <p>{@link #specHaversine}/{@link #specTotal} 은 <b>스펙 공식(구면 하버사인 R=6,371,000)의 독립 재구현</b>
 * 이다 — 정제 거리와 비교할 <b>원시 기준선</b>을 도출하기 위함이며 함수-under-test 실행 역산이 아니다.
 */
public final class TrackTestFixtures {

    public static final double BASE_LAT = 37.5;
    public static final double BASE_LNG = 127.0;
    /** 자오선 1° 위도의 미터(= R·π/180). 자오선 하버사인은 이 값·Δlat 로 정확. */
    public static final double MER = 6_371_000.0 * Math.PI / 180.0;   // 111194.9266...
    /** {@link TrackGeo} 점-폴리라인 근사가 쓰는 위도 미터/도 상수. */
    public static final double MPDLAT = 111_320.0;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TrackTestFixtures() {
    }

    /** 북쪽 {@code northM} m 지점의 위도(자오선 — 하버사인 거리 = northM 정확). */
    public static double lat(double northM) {
        return BASE_LAT + northM / MER;
    }

    /** 동쪽 {@code eastM} m 지점의 경도(코리도 근사와 정합). */
    public static double lng(double eastM) {
        return BASE_LNG + eastM / (MPDLAT * Math.cos(Math.toRadians(BASE_LAT)));
    }

    public static TrackCoord coord(double northM, double eastM) {
        return new TrackCoord(lat(northM), lng(eastM));
    }

    public static TrackPoint point(double northM, double eastM, long tsMillis) {
        return point(northM, eastM, tsMillis, 8.0);
    }

    /** accuracy 지정(정제 필터 경계 테스트용). speed·altitude 는 판정 미사용이라 상수/ null. */
    public static TrackPoint point(double northM, double eastM, long tsMillis, double accuracy) {
        return new TrackPoint(lat(northM), lng(eastM), tsMillis, 3.0, accuracy, null);
    }

    // --- 스펙 하버사인 독립 재구현(원시 기준선 도출용) ---

    public static double specHaversine(TrackCoord a, TrackCoord b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000.0 * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    public static int specTotal(List<TrackPoint> points) {
        double sum = 0.0;
        for (int i = 1; i < points.size(); i++) {
            sum += specHaversine(points.get(i - 1).coord(), points.get(i).coord());
        }
        return (int) Math.round(sum);
    }

    // --- 픽스처 로더(계약 shape → List<TrackPoint>) ---

    /**
     * {@code fixtures/tracks/synthetic/{name}} JSON(업로드 계약 shape)을 읽어 정제 입력 포인트열로 변환.
     * 폴리라인(1e5)을 디코딩하고 병렬 배열(timestamps/speeds/accuracies/altitudes?)을 zip 한다.
     */
    public static List<TrackPoint> loadUpload(String name) {
        String path = "/fixtures/tracks/synthetic/" + name;
        try (InputStream in = TrackTestFixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("픽스처 없음: " + path);
            }
            JsonNode root = MAPPER.readTree(in);
            List<TrackCoord> coords = TrackPolylineCodec.decode(root.get("polyline").asText());
            JsonNode ts = root.get("timestamps");
            JsonNode sp = root.get("speeds");
            JsonNode ac = root.get("accuracies");
            JsonNode al = root.get("altitudes");
            if (ts.size() != coords.size() || sp.size() != coords.size()
                    || ac.size() != coords.size()) {
                throw new IllegalStateException("픽스처 배열 길이 불일치(TK-1): " + name);
            }
            List<TrackPoint> points = new ArrayList<>(coords.size());
            for (int i = 0; i < coords.size(); i++) {
                Double alt = (al != null && !al.isNull() && i < al.size()) ? al.get(i).asDouble()
                        : null;
                points.add(new TrackPoint(coords.get(i).lat(), coords.get(i).lng(),
                        ts.get(i).asLong(), sp.get(i).asDouble(), ac.get(i).asDouble(), alt));
            }
            return points;
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
