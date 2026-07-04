package com.runningcrew.tracking.domain.event;

/**
 * 트랙 업로드 사실(과거형). 업로드 커밋 후 Race가 전원 업로드 여부를 재평가하는 트리거(A10).
 *
 * <p>Race 컨텍스트가 <b>AFTER_COMMIT</b> 리스너로만 소비한다(직접 리포지토리 호출 0, EV-1). 이벤트는
 * 반드시 {@code {ctx}/domain/event/}에 둔다(ArchUnit R-2 전제 — 타 컨텍스트는 domain.event만 import 허용).
 */
public record TrackUploaded(Long sessionId, Long userId) {
}
