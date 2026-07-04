package com.runningcrew.ranking.domain.event;

/**
 * 순위 확정 사실(과거형) — Race가 소비해 세션을 FINALIZING→COMPLETED로 전이한다(설계 42 §5.3).
 * M3에서는 이 이벤트의 AFTER_COMMIT 리스너가 리플레이 스냅샷 생성을 시작한다(범위 밖).
 *
 * <p>이벤트는 {@code {ctx}/domain/event/}에 둔다(ArchUnit R-2 전제).
 */
public record ResultFinalized(Long sessionId, Long resultId) {
}
