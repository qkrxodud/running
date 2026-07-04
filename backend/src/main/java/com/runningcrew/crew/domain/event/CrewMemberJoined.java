package com.runningcrew.crew.domain.event;

/**
 * 크루 합류 사실(과거형). B1에서는 소비자 없이 발행만 한다(로그 소비자 1개 허용) —
 * O-1(인앱 갈음, 별도 알림함/FCM 없음) 확정. 결정 번복 시 소비자만 추가하면 되는 확장 지점 보존.
 *
 * <p>이벤트는 반드시 {@code {ctx}/domain/event/}에 둔다(ArchUnit R-2 전제).
 */
public record CrewMemberJoined(Long crewId, Long userId) {
}
