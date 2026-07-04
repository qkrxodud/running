import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import 'notification_service.dart';

/// [NotificationService] 의 Android 구현. 상시 알림 갱신을
/// Foreground Service 알림(flutter_foreground_task)에 위임한다.
///
/// **Android 결합 주의:** Android 에서 트래킹 상시 알림은 Foreground Service 의
/// 알림과 동일 메커니즘이다. 서비스 시작/종료(=알림 표시/제거)의 lifecycle 은
/// [AndroidForegroundTracker] 가 소유하고, 이 서비스는 알림 "내용 갱신"을
/// 담당한다. [startTrackingNotification]/[stopTrackingNotification] 은 서비스가
/// 떠 있을 때만 유효하며(내용 반영), iOS 확장 시 로컬 알림 구현으로 대체된다.
///
/// channelId/importance 등 플랫폼 세부는 이 구현체 내부 상수로만 존재한다.
class AndroidNotificationService implements NotificationService {
  const AndroidNotificationService();

  @override
  Future<void> startTrackingNotification({
    required String title,
    required String body,
  }) async {
    // 서비스가 이미 떠 있으면 내용 반영. 서비스 시작 자체는 트래커가 수행한다.
    if (await FlutterForegroundTask.isRunningService) {
      await FlutterForegroundTask.updateService(
        notificationTitle: title,
        notificationText: body,
      );
    }
  }

  @override
  Future<void> updateTrackingNotification({
    required String title,
    required String body,
  }) async {
    if (await FlutterForegroundTask.isRunningService) {
      await FlutterForegroundTask.updateService(
        notificationTitle: title,
        notificationText: body,
      );
    }
  }

  @override
  Future<void> stopTrackingNotification() async {
    // 알림 제거 = 서비스 종료. 트래커가 stop() 에서 서비스를 내리므로 여기선 no-op.
  }
}
