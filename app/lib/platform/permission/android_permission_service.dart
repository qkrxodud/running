import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:geolocator/geolocator.dart';

import 'permission_service.dart';

/// [PermissionService] 의 Android 구현. geolocator/flutter_foreground_task 의
/// OS별 권한 API를 [PermissionStatus] 로 정규화한다.
///
/// 스파이크 `_ensurePermissions()` 의 위치/알림/배터리최적화 단계를 그대로 옮겼다.
/// Android 전용 import(geolocator, flutter_foreground_task)는 이 구현체 안에만 있다.
class AndroidPermissionService implements PermissionService {
  const AndroidPermissionService();

  @override
  Future<TrackingPermissions> check() async {
    final location = _mapLocation(await Geolocator.checkPermission());
    final notification =
        _mapNotification(await FlutterForegroundTask.checkNotificationPermission());
    return TrackingPermissions(location: location, notification: notification);
  }

  @override
  Future<PermissionStatus> requestLocation() async {
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    return _mapLocation(permission);
  }

  @override
  Future<PermissionStatus> requestNotification() async {
    final result = await FlutterForegroundTask.requestNotificationPermission();
    return _mapNotification(result);
  }

  @override
  Future<void> requestIgnoreBatteryOptimization() async {
    // 삼성 앱 자동 종료 대응. 이미 예외면 재요청하지 않는다.
    if (!await FlutterForegroundTask.isIgnoringBatteryOptimizations) {
      await FlutterForegroundTask.requestIgnoreBatteryOptimization();
    }
  }

  @override
  Future<bool> isLocationServiceEnabled() =>
      Geolocator.isLocationServiceEnabled();

  PermissionStatus _mapLocation(LocationPermission p) {
    switch (p) {
      case LocationPermission.always:
      case LocationPermission.whileInUse:
        return PermissionStatus.granted;
      case LocationPermission.denied:
        return PermissionStatus.denied;
      case LocationPermission.deniedForever:
        return PermissionStatus.permanentlyDenied;
      case LocationPermission.unableToDetermine:
        return PermissionStatus.denied;
    }
  }

  PermissionStatus _mapNotification(NotificationPermission p) {
    switch (p) {
      case NotificationPermission.granted:
        return PermissionStatus.granted;
      case NotificationPermission.denied:
        return PermissionStatus.denied;
      case NotificationPermission.permanently_denied:
        return PermissionStatus.permanentlyDenied;
    }
  }
}
