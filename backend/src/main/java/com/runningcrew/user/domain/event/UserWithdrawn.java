package com.runningcrew.user.domain.event;

/**
 * 회원 탈퇴 사실(과거형). user 컨텍스트가 발행하고 crew(멤버십 정리·승계)·tracking(위치 원본 파기)이
 * <b>동기 @EventListener(같은 트랜잭션)</b>으로 소비한다 — 탈퇴와 후속 정리가 원자적이어야 하기 때문.
 *
 * <p>이벤트는 반드시 {@code {ctx}/domain/event/} 패키지에 둔다(ArchUnit R-2의 전제 — 소비측이 import 가능).
 */
public record UserWithdrawn(Long userId) {
}
