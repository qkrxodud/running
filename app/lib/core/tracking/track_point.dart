/// GPS 단일 샘플. 계획서 5.3의 TrackPoint VO와 동일한 필드 구성.
///
/// timestamp는 기기 시계가 아닌 GPS 시각(위치 프레임워크가 주는 시각)을 사용한다
/// — 기기 시계 오차로 인한 기록·동기화 왜곡 방지(계획서 설계 노트).
class TrackPoint {
  const TrackPoint({
    required this.timestamp,
    required this.lat,
    required this.lng,
    required this.altitude,
    required this.speed,
    required this.accuracy,
  });

  final DateTime timestamp; // UTC
  final double lat;
  final double lng;
  final double altitude; // meters
  final double speed; // m/s
  final double accuracy; // meters (수평 정확도)

  Map<String, dynamic> toJson() => {
        'ts': timestamp.toUtc().toIso8601String(),
        'lat': lat,
        'lng': lng,
        'alt': altitude,
        'spd': speed,
        'acc': accuracy,
      };

  factory TrackPoint.fromJson(Map<String, dynamic> json) => TrackPoint(
        timestamp: DateTime.parse(json['ts'] as String),
        lat: (json['lat'] as num).toDouble(),
        lng: (json['lng'] as num).toDouble(),
        altitude: (json['alt'] as num).toDouble(),
        speed: (json['spd'] as num).toDouble(),
        accuracy: (json['acc'] as num).toDouble(),
      );
}
