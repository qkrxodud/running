package com.runningcrew.race.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Encoded Polyline Algorithm(zigzag + 5비트 청크). <b>순수 함수</b> — IO·시계·랜덤 없음(골든 대상).
 *
 * <p>클라 {@code app/lib/core/geo/polyline_codec.dart}와 <b>문자 단위 상호운용</b>이 계약(course-api.md).
 * <ul>
 *   <li>precision: <b>1e5</b>(좌표 ×100000) — 1e6 아님. 클라 {@code PolylineCodec._precision=100000}과 일치.
 *   <li>tie 반올림: <b>half-away-from-zero</b>(정확히 .5는 절댓값 큰 쪽) — Dart {@code num.round()}와 동형.
 *       {@code Math.round()}(half-up)는 음위도/음경도에서 갈리므로 encode에서 부호 분리로 회피한다.
 * </ul>
 * decode는 정수 누적 + {@code /1e5} 나눗셈뿐이라 반올림이 개입하지 않아 클라와 자연히 일치한다.
 */
public final class PolylineCodec {

    private static final double PRECISION = 100000.0;   // 1e5
    private static final int ASCII_OFFSET = 63;
    private static final int CHUNK_MASK = 0x1f;
    private static final int CONTINUATION_BIT = 0x20;

    private PolylineCodec() {
    }

    /** 좌표열 → 인코딩 문자열. 빈 입력은 빈 문자열. */
    public static String encode(List<LatLng> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0;
        long prevLng = 0;
        for (LatLng p : points) {
            long lat = round1e5(p.lat());
            long lng = round1e5(p.lng());
            encodeValue(lat - prevLat, sb);
            encodeValue(lng - prevLng, sb);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    /**
     * 인코딩 문자열 → 좌표열. 빈 문자열은 빈 목록.
     *
     * @throws InvalidCourseException 청크가 위/경도 쌍으로 끝나지 않는 등 손상된 입력
     */
    public static List<LatLng> decode(String encoded) {
        List<LatLng> result = new ArrayList<>();
        int index = 0;
        long lat = 0;
        long lng = 0;
        int len = encoded.length();
        while (index < len) {
            long[] dLat = decodeValue(encoded, index, len);
            lat += dLat[0];
            index = (int) dLat[1];
            if (index >= len) {
                throw new InvalidCourseException("폴리라인이 위/경도 쌍으로 끝나지 않습니다.");
            }
            long[] dLng = decodeValue(encoded, index, len);
            lng += dLng[0];
            index = (int) dLng[1];
            result.add(new LatLng(lat / PRECISION, lng / PRECISION));
        }
        return result;
    }

    /** half-away-from-zero 반올림(부호 분리) — {@code Math.round()}의 음수 오차를 회피. */
    private static long round1e5(double coord) {
        double scaled = coord * PRECISION;
        return scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
    }

    private static void encodeValue(long value, StringBuilder sb) {
        long v = value < 0 ? ~(value << 1) : (value << 1);   // zigzag
        while (v >= CONTINUATION_BIT) {
            sb.append((char) ((CONTINUATION_BIT | (v & CHUNK_MASK)) + ASCII_OFFSET));
            v >>= 5;
        }
        sb.append((char) (v + ASCII_OFFSET));
    }

    /** @return {@code [value, nextIndex]} */
    private static long[] decodeValue(String encoded, int startIndex, int len) {
        int index = startIndex;
        int shift = 0;
        long result = 0;
        int b;
        do {
            if (index >= len) {
                throw new InvalidCourseException("폴리라인 청크가 완결되지 않았습니다.");
            }
            b = encoded.charAt(index) - ASCII_OFFSET;
            result |= (long) (b & CHUNK_MASK) << shift;
            shift += 5;
            index++;
        } while (b >= CONTINUATION_BIT);
        long value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new long[] {value, index};
    }
}
