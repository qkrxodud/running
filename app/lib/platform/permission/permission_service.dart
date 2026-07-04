/// 권한 상태(플랫폼 무관). OS별 세분 상태를 4값으로 정규화한다.
enum PermissionStatus { granted, denied, permanentlyDenied, restricted }

/// 트래킹에 필요한 권한 묶음의 스냅샷.
class TrackingPermissions {
  const TrackingPermissions({
    required this.location,
    required this.notification,
  });

  final PermissionStatus location;
  final PermissionStatus notification;

  /// 위치 권한이 허용이면 트래킹 가능(알림은 상시 알림 품질 문제일 뿐 트래킹
  /// 자체를 막지 않는다).
  bool get canTrack => location == PermissionStatus.granted;
}

/// 권한 플로우 격리 지점 ③ — OS별 분기를 구현체로 미룬다.
///
/// 위치 권한은 포그라운드 서비스 방식이므로 "앱 사용 중"이면 충분하다
/// (ACCESS_BACKGROUND_LOCATION 요청 금지 — Play 심사 우회가 설계 의도).
abstract interface class PermissionService {
  /// 현재 권한 상태 조회(요청 없음).
  Future<TrackingPermissions> check();

  /// 위치 권한 요청.
  Future<PermissionStatus> requestLocation();

  /// 알림 권한 요청(Android 13+ / iOS). 상시 기록 알림의 조건.
  Future<PermissionStatus> requestNotification();

  /// 배터리 최적화 예외 요청(삼성 앱 자동 종료 대응). 미지원 플랫폼은 no-op.
  Future<void> requestIgnoreBatteryOptimization();

  /// OS 위치 서비스(GPS) 켜짐 여부.
  Future<bool> isLocationServiceEnabled();
}
