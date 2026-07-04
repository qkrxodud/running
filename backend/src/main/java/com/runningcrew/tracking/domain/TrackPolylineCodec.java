package com.runningcrew.tracking.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Encoded Polyline(precision 1e5) 디코딩/인코딩 — <b>순수 함수</b>(ArchUnit R-1, 골든 전제).
 *
 * <p>계약 track-api §0: 좌표열은 course-api와 <b>동일 규약</b>(1e5, tie=half-away-from-zero)이며 클라
 * {@code PolylineCodec}과 문자 단위 상호운용된다. tracking 로컬 구현 — race {@code PolylineCodec}을
 * 참조하지 않는다(R-2: 컨텍스트 간 클래스 의존 금지). decode는 정수 누적+나눗셈이라 반올림 무개입.
 */
public final class TrackPolylineCodec {

    private static final double PRECISION = 100000.0;   // 1e5
    private static final int ASCII_OFFSET = 63;
    private static final int CHUNK_MASK = 0x1f;
    private static final int CONTINUATION_BIT = 0x20;

    private TrackPolylineCodec() {
    }

    /**
     * 인코딩 문자열 → 좌표열. 빈 문자열은 빈 목록.
     *
     * @throws InvalidTrackPayloadException 청크가 위/경도 쌍으로 끝나지 않는 등 손상된 입력
     */
    public static List<TrackCoord> decode(String encoded) {
        List<TrackCoord> result = new ArrayList<>();
        int index = 0;
        long lat = 0;
        long lng = 0;
        int len = encoded.length();
        while (index < len) {
            long[] dLat = decodeValue(encoded, index, len);
            lat += dLat[0];
            index = (int) dLat[1];
            if (index >= len) {
                throw new InvalidTrackPayloadException("폴리라인이 위/경도 쌍으로 끝나지 않습니다.");
            }
            long[] dLng = decodeValue(encoded, index, len);
            lng += dLng[0];
            index = (int) dLng[1];
            result.add(new TrackCoord(lat / PRECISION, lng / PRECISION));
        }
        return result;
    }

    /** 좌표열 → 인코딩 문자열(정제 폴리라인 재직렬화용). half-away-from-zero. */
    public static String encode(List<TrackCoord> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0;
        long prevLng = 0;
        for (TrackCoord p : points) {
            long lat = round1e5(p.lat());
            long lng = round1e5(p.lng());
            encodeValue(lat - prevLat, sb);
            encodeValue(lng - prevLng, sb);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    private static long round1e5(double coord) {
        double scaled = coord * PRECISION;
        return scaled < 0 ? -Math.round(-scaled) : Math.round(scaled);
    }

    private static void encodeValue(long value, StringBuilder sb) {
        long v = value < 0 ? ~(value << 1) : (value << 1);
        while (v >= CONTINUATION_BIT) {
            sb.append((char) ((CONTINUATION_BIT | (v & CHUNK_MASK)) + ASCII_OFFSET));
            v >>= 5;
        }
        sb.append((char) (v + ASCII_OFFSET));
    }

    private static long[] decodeValue(String encoded, int startIndex, int len) {
        int index = startIndex;
        int shift = 0;
        long result = 0;
        int b;
        do {
            if (index >= len) {
                throw new InvalidTrackPayloadException("폴리라인 청크가 완결되지 않았습니다.");
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
