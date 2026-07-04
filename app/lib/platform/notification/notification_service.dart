/// 상시 알림 격리 지점 ② — OS 종속을 이 인터페이스 뒤로 숨긴다.
/// Android=Foreground Service 상시 알림, iOS(후일)=로컬 알림.
///
/// 플랫폼 종속 세부(channelId, importance, 알림 채널)는 계약/코어에 노출하지
/// 않는다 — 구현체 내부 상수. 서버 무관.
abstract interface class NotificationService {
  /// 트래킹 상시 알림 시작.
  Future<void> startTrackingNotification({
    required String title,
    required String body,
  });

  /// 상시 알림 내용 갱신(포인트 수·정확도 등). 저빈도 호출 권장.
  Future<void> updateTrackingNotification({
    required String title,
    required String body,
  });

  /// 상시 알림 종료.
  Future<void> stopTrackingNotification();
}
