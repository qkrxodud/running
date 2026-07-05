package com.runningcrew.replay.application.port.out;

/**
 * "리플레이 열림" FCM 세션당 1회 멱등 게이트(RP-12, M3-C). 최초 READY 도달 시에만 알림을 트리거하기 위해
 * {@code race_session.replay_notified_at}를 <b>원자적 check-and-set</b>한다 — 재생성 READY·중복 이벤트에
 * 재발송하지 않는다.
 */
public interface ReplayNotificationGate {

    /**
     * {@code replay_notified_at}이 NULL이면 now로 set하고 true, 이미 set이면 false(원자적 UPDATE …
     * WHERE replay_notified_at IS NULL). true일 때만 발송.
     */
    boolean markNotifiedIfFirst(Long sessionId);

    /** 알림 수신 대상(세션 참가자 user_id) — 스텁 로그·실 FCM 토큰 조인용. */
    java.util.List<Long> participantUserIds(Long sessionId);
}
