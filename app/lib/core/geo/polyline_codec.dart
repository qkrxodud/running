import 'lat_lng.dart';

/// Google Encoded Polyline Algorithm Format 구현 (precision 1e-5).
///
/// 순수 함수 — IO·시계·랜덤 없음. 트랙 전송 형식(계약: 인코딩 폴리라인 +
/// 병렬 배열)의 좌표열 인코딩 담당. 서버/클라 양쪽이 같은 규칙을 써야 하므로
/// 표준 알고리즘(1e5 스케일, zigzag, 5비트 청크)을 그대로 따른다.
///
/// 참고: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
class PolylineCodec {
  const PolylineCodec._();

  static const int _precision = 100000; // 1e5

  /// 좌표열 → 인코딩 문자열. 빈 입력은 빈 문자열.
  static String encode(List<LatLng> points) {
    final buffer = StringBuffer();
    int prevLat = 0;
    int prevLng = 0;
    for (final p in points) {
      final lat = (p.lat * _precision).round();
      final lng = (p.lng * _precision).round();
      _encodeValue(lat - prevLat, buffer);
      _encodeValue(lng - prevLng, buffer);
      prevLat = lat;
      prevLng = lng;
    }
    return buffer.toString();
  }

  /// 인코딩 문자열 → 좌표열. 빈 문자열은 빈 목록.
  static List<LatLng> decode(String encoded) {
    final result = <LatLng>[];
    int index = 0;
    int lat = 0;
    int lng = 0;
    final len = encoded.length;
    while (index < len) {
      final dLat = _decodeValue(encoded, index);
      index = dLat.nextIndex;
      lat += dLat.value;
      final dLng = _decodeValue(encoded, index);
      index = dLng.nextIndex;
      lng += dLng.value;
      result.add(LatLng(lat / _precision, lng / _precision));
    }
    return result;
  }

  static void _encodeValue(int value, StringBuffer buffer) {
    // zigzag: 부호를 최하위 비트로 인코딩.
    int v = value < 0 ? ~(value << 1) : (value << 1);
    while (v >= 0x20) {
      buffer.writeCharCode((0x20 | (v & 0x1f)) + 63);
      v >>= 5;
    }
    buffer.writeCharCode(v + 63);
  }

  static _Decoded _decodeValue(String encoded, int startIndex) {
    int index = startIndex;
    int shift = 0;
    int result = 0;
    int b;
    do {
      b = encoded.codeUnitAt(index) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
      index++;
    } while (b >= 0x20);
    final value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
    return _Decoded(value, index);
  }
}

class _Decoded {
  const _Decoded(this.value, this.nextIndex);
  final int value;
  final int nextIndex;
}
