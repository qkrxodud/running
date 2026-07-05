package com.runningcrew.replay.application;

import com.runningcrew.ranking.domain.event.ResultFinalized;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link ResultFinalized} 소비(A5) — 순위 확정 <b>커밋 후 비동기</b> 스냅샷 생성. AFTER_COMMIT + {@code @Async}로
 * 확정 트랜잭션과 분리(확정이 계산에 인질 안 잡힘, RP-9). 생성은 {@code REQUIRES_NEW}로 새 트랜잭션(서비스).
 *
 * <p>ranking→replay 직접 호출 없이 이벤트 경유(ranking.domain.event만 import). 예외는 서비스가 FAILED로 흡수.
 */
@Component
public class ResultFinalizedReplayListener {

    private final ReplayGenerationService generationService;

    public ResultFinalizedReplayListener(ReplayGenerationService generationService) {
        this.generationService = generationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ResultFinalized event) {
        generationService.generate(event.sessionId());
    }
}
