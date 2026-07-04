package com.runningcrew.race.application;

import com.runningcrew.tracking.domain.event.TrackUploaded;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link TrackUploaded} 소비(A10, EV-1) — 업로드 <b>커밋 후</b> 전원 업로드 여부 재평가. 별도 빈으로 두어
 * 자기호출 없이 {@link SessionFinalizationService}의 새 트랜잭션 경계가 적용되게 한다.
 *
 * <p>tracking→race 직접 리포지토리 호출 없이 이벤트 경유만(ArchUnit R-2: tracking.domain.event만 import).
 * 확정이 업로드 트랜잭션을 인질로 잡지 않도록 AFTER_COMMIT(설계 42 §5.3).
 */
@Component
public class TrackUploadedListener {

    private final SessionFinalizationService finalizationService;

    public TrackUploadedListener(SessionFinalizationService finalizationService) {
        this.finalizationService = finalizationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TrackUploaded event) {
        finalizationService.tryFinalizeIfAllUploaded(event.sessionId());
    }
}
