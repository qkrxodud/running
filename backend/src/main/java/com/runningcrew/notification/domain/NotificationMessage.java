package com.runningcrew.notification.domain;

import java.util.List;
import java.util.Map;

/**
 * 발송 메시지(순수 — 프레임워크 무관). FCM data payload에 딥링크를 탑재한다(conventions §10). 실 FCM 어댑터가
 * 교체돼도 이 계약은 불변 — 스텁/실 어댑터가 동일 메시지를 소비한다.
 *
 * @param type            알림 종류(리마인더·리플레이 열림)
 * @param sessionId       대상 세션(멱등·딥링크 키)
 * @param recipientUserIds 수신 대상 user_id(실 어댑터가 device_token 조인 — 스텁은 로그)
 * @param title           표시 제목
 * @param body            표시 본문
 * @param data            FCM data 필드(반드시 {@code deep_link} 포함 — 클라 라우팅, §10)
 */
public record NotificationMessage(
        NotificationType type,
        Long sessionId,
        List<Long> recipientUserIds,
        String title,
        String body,
        Map<String, String> data) {

    public enum NotificationType {
        SESSION_REMINDER,
        REPLAY_READY
    }

    public String deepLink() {
        return data.get("deep_link");
    }
}
