package com.runningcrew.ranking.application;

import com.runningcrew.race.domain.event.RaceCompleted;
import com.runningcrew.race.domain.event.RaceCompleted.FinalizedParticipant;
import com.runningcrew.ranking.application.port.out.PriorPbPort;
import com.runningcrew.ranking.application.port.out.SaveRankingResultPort;
import com.runningcrew.ranking.domain.RankedEntry;
import com.runningcrew.ranking.domain.RankingInput;
import com.runningcrew.ranking.domain.RankingPolicy;
import com.runningcrew.ranking.domain.ResultStatus;
import com.runningcrew.ranking.domain.event.ResultFinalized;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@link RaceCompleted} 소비(A7) — RankingPolicy(순수)로 순위를 산정해 저장하고 {@link ResultFinalized}를
 * 발행한다. 동기 {@code @EventListener}(확정 트랜잭션 내부, M2 동기 확정).
 *
 * <p>race→ranking 직접 호출 없이 이벤트 경유만(ArchUnit R-2: race.domain.event만 import). PB는 유저×코스
 * 과거 세션 최소 완주기록과 비교(설계 42 §5.2) — {@link PriorPbPort}로 조회.
 */
@Component
public class RankingFinalizationListener {

    private final PriorPbPort priorPbPort;
    private final SaveRankingResultPort savePort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RankingFinalizationListener(PriorPbPort priorPbPort, SaveRankingResultPort savePort,
                                       ApplicationEventPublisher eventPublisher, Clock clock) {
        this.priorPbPort = priorPbPort;
        this.savePort = savePort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @EventListener
    public void on(RaceCompleted event) {
        List<RankingInput> inputs = event.participants().stream()
                .map(p -> toInput(event, p))
                .toList();
        List<RankedEntry> ranked = RankingPolicy.rank(inputs);
        Long resultId = savePort.save(event.sessionId(), clock.instant(), ranked);
        eventPublisher.publishEvent(new ResultFinalized(event.sessionId(), resultId));
    }

    private RankingInput toInput(RaceCompleted event, FinalizedParticipant p) {
        ResultStatus status = ResultStatus.valueOf(p.status());
        Integer priorPb = priorPbPort.findPriorPbTimeS(event.courseId(), p.userId(),
                event.sessionId());
        return new RankingInput(p.userId(), status, p.recordTimeS(), priorPb);
    }
}
