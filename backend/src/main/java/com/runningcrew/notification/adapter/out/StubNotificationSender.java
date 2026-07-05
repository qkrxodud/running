package com.runningcrew.notification.adapter.out;

import com.runningcrew.notification.application.port.out.NotificationSender;
import com.runningcrew.notification.domain.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 스텁 알림 어댑터(M3-C) — 실 FCM 대신 <b>구조화 로그</b>로 발송을 기록한다. Firebase 발급 시 실 FCM
 * 어댑터로 교체(같은 {@link NotificationSender} 계약). 기본 빈 — 실 어댑터가 {@code @Primary}로 대체.
 *
 * <p>로그는 M4 발송 관측/조회율 측정과 정합하는 형태(type·session·수신자 수·deep_link). 개인정보는
 * user_id만(닉네임·위치 없음).
 */
@Component
public class StubNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(StubNotificationSender.class);

    @Override
    public void send(NotificationMessage message) {
        log.info("notification_sent type={} session_id={} recipients={} deep_link={} title=\"{}\"",
                message.type(), message.sessionId(),
                message.recipientUserIds() == null ? 0 : message.recipientUserIds().size(),
                message.deepLink(), message.title());
    }
}
