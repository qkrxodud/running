package com.runningcrew.race.application;

import com.runningcrew.race.application.port.out.RaceSessionRepository;
import com.runningcrew.race.domain.RaceStatus;
import com.runningcrew.ranking.domain.event.ResultFinalized;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@link ResultFinalized} 소비(설계 42 §5.3) — 순위 확정 후 세션을 FINALIZING→COMPLETED로 전이한다.
 * 동기 {@code @EventListener}(확정 트랜잭션 내부) — M2는 FINALIZING→COMPLETED가 동기 확정이다.
 *
 * <p>ranking→race 직접 호출 없이 이벤트 경유만(ArchUnit R-2: ranking.domain.event만 import).
 */
@Component
public class ResultFinalizedListener {

    private final RaceSessionRepository sessionRepository;

    public ResultFinalizedListener(RaceSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @EventListener
    public void on(ResultFinalized event) {
        sessionRepository.findById(event.sessionId()).ifPresent(session -> {
            if (session.getStatus() == RaceStatus.FINALIZING) {
                session.complete();
                sessionRepository.save(session);
            }
        });
    }
}
