package com.runningcrew.race.application;

import com.runningcrew.race.application.port.out.SessionSchedulingPort;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마감 스케줄러(A9) — 주기 폴링으로 {@code upload_deadline} 도달한 미확정 세션을 확정한다. 각 세션 확정은
 * {@link SessionFinalizationService#finalizeByDeadline}의 개별 트랜잭션에서 <b>idempotent</b>하게 처리되어
 * 중복 실행·재기동에 내성이 있다. 시각은 주입된 {@link Clock}(UTC)만 사용한다.
 */
@Component
public class SessionCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionCloseScheduler.class);

    private final SessionSchedulingPort schedulingPort;
    private final SessionFinalizationService finalizationService;
    private final Clock clock;

    public SessionCloseScheduler(SessionSchedulingPort schedulingPort,
                                 SessionFinalizationService finalizationService,
                                 Clock clock) {
        this.schedulingPort = schedulingPort;
        this.finalizationService = finalizationService;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${race.finalize-scheduler.initial-delay-ms:30000}",
            fixedDelayString = "${race.finalize-scheduler.fixed-delay-ms:60000}")
    public void closeDeadlineReachedSessions() {
        List<Long> sessionIds = schedulingPort.findDeadlineReachedSessionIds(clock.instant());
        for (Long sessionId : sessionIds) {
            try {
                finalizationService.finalizeByDeadline(sessionId);
            } catch (RuntimeException e) {
                log.warn("세션 마감 확정 실패 sessionId={}", sessionId, e);   // 다음 세션 계속
            }
        }
    }
}
