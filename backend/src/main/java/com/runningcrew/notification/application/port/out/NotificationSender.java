package com.runningcrew.notification.application.port.out;

import com.runningcrew.notification.domain.NotificationMessage;

/**
 * 알림 발송 out-port(M3-C). 도메인·스케줄러는 이 포트에만 의존한다 — 실 FCM 어댑터는 Firebase 발급 게이트
 * 뒤에서 <b>어댑터 1개 교체</b>로 활성(fail-safe 구조). MVP는 스텁(구조화 로그) 어댑터.
 */
public interface NotificationSender {

    void send(NotificationMessage message);
}
