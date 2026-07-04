/// 순수 좌표 값 객체 (플랫폼 무관). 폴리라인 인코딩·경로 계산의 입출력 단위.
///
/// TrackPoint(GPS 샘플)와 달리 시각·속도·정확도가 없는 순수 위치다 —
/// 폴리라인은 좌표열만 다루므로 별도 값 객체로 분리한다.
class LatLng {
  const LatLng(this.lat, this.lng);

  final double lat;
  final double lng;

  @override
  bool operator ==(Object other) =>
      other is LatLng && other.lat == lat && other.lng == lng;

  @override
  int get hashCode => Object.hash(lat, lng);

  @override
  String toString() => 'LatLng($lat, $lng)';
}
