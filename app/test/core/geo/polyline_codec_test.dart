import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/geo/lat_lng.dart';
import 'package:running/core/geo/polyline_codec.dart';

void main() {
  group('PolylineCodec', () {
    test('Google 표준 예제를 인코딩한다', () {
      // https://developers.google.com/maps/documentation/utilities/polylinealgorithm
      final points = [
        const LatLng(38.5, -120.2),
        const LatLng(40.7, -120.95),
        const LatLng(43.252, -126.453),
      ];
      expect(PolylineCodec.encode(points), r'_p~iF~ps|U_ulLnnqC_mqNvxq`@');
    });

    test('표준 문자열을 디코딩한다', () {
      final decoded = PolylineCodec.decode(r'_p~iF~ps|U_ulLnnqC_mqNvxq`@');
      expect(decoded.length, 3);
      expect(decoded[0].lat, closeTo(38.5, 1e-5));
      expect(decoded[0].lng, closeTo(-120.2, 1e-5));
      expect(decoded[2].lat, closeTo(43.252, 1e-5));
      expect(decoded[2].lng, closeTo(-126.453, 1e-5));
    });

    test('encode→decode 왕복은 1e-5 정밀도 내에서 좌표를 보존한다', () {
      final points = [
        const LatLng(37.5665, 126.9780),
        const LatLng(37.5651, 126.9895),
        const LatLng(37.5700, 126.9820),
      ];
      final roundTrip = PolylineCodec.decode(PolylineCodec.encode(points));
      expect(roundTrip.length, points.length);
      for (var i = 0; i < points.length; i++) {
        expect(roundTrip[i].lat, closeTo(points[i].lat, 1e-5));
        expect(roundTrip[i].lng, closeTo(points[i].lng, 1e-5));
      }
    });

    test('빈 입력은 빈 문자열/빈 목록', () {
      expect(PolylineCodec.encode(const []), '');
      expect(PolylineCodec.decode(''), isEmpty);
    });
  });

  group('PolylineCodec 경계·상호운용 골든', () {
    // 아래 기대값은 구현 실행이 아니라 Google 표준 알고리즘 문서에서 도출한
    // 상호운용 기준(박제)이다 — 서버(Java) 구현이 같은 벡터를 만족해야 한다.
    // https://developers.google.com/maps/documentation/utilities/polylinealgorithm

    test('원점 단일 점은 "??" 로 인코딩된다 (델타 0,0 = 63,63)', () {
      // (0,0): lat 델타 0 → 0<<1=0 → chr(0+63)="?", lng 델타 0 → "?".
      expect(PolylineCodec.encode(const [LatLng(0, 0)]), '??');
      final decoded = PolylineCodec.decode('??');
      expect(decoded.length, 1);
      expect(decoded.single.lat, 0.0);
      expect(decoded.single.lng, 0.0);
    });

    test('단일 점(38.5,-120.2)은 표준 3점 벡터의 첫 두 청크와 같다', () {
      // Google 문서: 위도 38.5 → "_p~iF", 경도 -120.2 → "~ps|U".
      // 3점 벡터의 첫 점은 (0,0) 기준 절대 델타이므로 앞 두 청크와 일치한다.
      expect(
        PolylineCodec.encode(const [LatLng(38.5, -120.2)]),
        r'_p~iF~ps|U',
      );
    });

    test('음수·경도 -180 근처(-179.9832104)는 문서 예제 "`~oia@" 를 만족한다', () {
      // Google 문서 워크스루의 대표 예제: -179.9832104 → "`~oia@".
      // lat 델타 0("?") + lng 청크 → 전체 "?`~oia@".
      const p = LatLng(0.0, -179.9832104);
      expect(PolylineCodec.encode(const [p]), r'?`~oia@');
      final back = PolylineCodec.decode(r'?`~oia@').single;
      expect(back.lat, closeTo(0.0, 1e-5));
      expect(back.lng, closeTo(-179.9832104, 1e-5));
    });

    test('경도 +180 근처(179.999990)도 왕복 1e-5 정밀도를 보존한다', () {
      const p = LatLng(0.0, 179.99999);
      final back = PolylineCodec.decode(PolylineCodec.encode(const [p])).single;
      expect(back.lat, closeTo(0.0, 1e-5));
      expect(back.lng, closeTo(179.99999, 1e-5));
    });

    test('음수 위·경도 단일 점 왕복 1e-5 (남반구·서반구)', () {
      const p = LatLng(-33.86882, -151.20930);
      final back = PolylineCodec.decode(PolylineCodec.encode(const [p])).single;
      expect(back.lat, closeTo(-33.86882, 1e-5));
      expect(back.lng, closeTo(-151.20930, 1e-5));
    });

    test('6번째 이하 소수 자리는 1e-5 양자화 후에도 오차 <= 1e-5', () {
      // 37.123456 → *1e5=3712345.6 → round 3712346 → 37.12346. 오차 4e-6.
      const p = LatLng(37.123456, 127.654329);
      final back = PolylineCodec.decode(PolylineCodec.encode(const [p])).single;
      expect(back.lat, closeTo(37.123456, 1e-5));
      expect(back.lng, closeTo(127.654329, 1e-5));
    });

    test('서울 한강 코스 수준 다점 트랙 전 구간 왕복 1e-5 보존', () {
      // 실좌표 수준(서울 한강 반포~잠실 방향) 위경도 다점.
      const course = [
        LatLng(37.51235, 126.99640),
        LatLng(37.51188, 127.00412),
        LatLng(37.51602, 127.01105),
        LatLng(37.52014, 127.02330),
        LatLng(37.51930, 127.03588),
        LatLng(37.51547, 127.04701),
      ];
      final back = PolylineCodec.decode(PolylineCodec.encode(course));
      expect(back.length, course.length);
      for (var i = 0; i < course.length; i++) {
        expect(back[i].lat, closeTo(course[i].lat, 1e-5),
            reason: 'course[$i].lat');
        expect(back[i].lng, closeTo(course[i].lng, 1e-5),
            reason: 'course[$i].lng');
      }
    });

    test('encode 와 decode 는 표준 벡터에서 서로 역함수다 (박제 상호운용)', () {
      const golden = r'_p~iF~ps|U_ulLnnqC_mqNvxq`@';
      const points = [
        LatLng(38.5, -120.2),
        LatLng(40.7, -120.95),
        LatLng(43.252, -126.453),
      ];
      expect(PolylineCodec.encode(points), golden);
      expect(PolylineCodec.encode(PolylineCodec.decode(golden)), golden);
    });
  });
}
