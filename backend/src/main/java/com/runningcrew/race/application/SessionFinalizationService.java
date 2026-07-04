package com.runningcrew.race.application;

import com.runningcrew.race.application.port.out.ParticipationRepository;
import com.runningcrew.race.application.port.out.RaceSessionRepository;
import com.runningcrew.race.application.port.out.TrackResultQueryPort;
import com.runningcrew.race.application.port.out.TrackResultQueryPort.TrackResult;
import com.runningcrew.race.domain.ParticipantClose;
import com.runningcrew.race.domain.ParticipantOutcome;
import com.runningcrew.race.domain.Participation;
import com.runningcrew.race.domain.RaceSession;
import com.runningcrew.race.domain.RaceStatus;
import com.runningcrew.race.domain.SessionClosePolicy;
import com.runningcrew.race.domain.event.RaceCompleted;
import com.runningcrew.race.domain.event.RaceCompleted.FinalizedParticipant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 마감·확정 오케스트레이션(A9·A10). SessionClosePolicy(순수)로 참가자를 최종화하고 FINALIZING
 * 전이 후 {@link RaceCompleted}를 발행한다 — Ranking이 동기 소비해 순위를 산정하고(A7), 되돌아온
 * ResultFinalized를 {@link ResultFinalizedListener}가 COMPLETED로 전이한다(설계 42 §5.3, M2 동기).
 *
 * <p>트리거: (1) TrackUploaded AFTER_COMMIT — 전원 업로드 시(A10), (2) 마감 스케줄러 — deadline 도달 시(A9).
 * 두 경로 모두 idempotent(이미 COMPLETED/CANCELLED면 no-op).
 */
@Service
public class SessionFinalizationService {

    private final RaceSessionRepository sessionRepository;
    private final ParticipationRepository participationRepository;
    private final TrackResultQueryPort trackResultQueryPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SessionFinalizationService(RaceSessionRepository sessionRepository,
                                      ParticipationRepository participationRepository,
                                      TrackResultQueryPort trackResultQueryPort,
                                      ApplicationEventPublisher eventPublisher,
                                      Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participationRepository = participationRepository;
        this.trackResultQueryPort = trackResultQueryPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * TrackUploaded 경로 — STARTED 전원 업로드일 때만 확정(A10). AFTER_COMMIT 리스너에서 호출되므로
     * <b>REQUIRES_NEW</b>(직전 트랜잭션은 이미 커밋 완료 — 새 트랜잭션을 강제해야 EM 쓰기가 가능).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryFinalizeIfAllUploaded(Long sessionId) {
        Optional<RaceSession> found = sessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            return;
        }
        RaceSession session = found.get();
        if (session.getStatus() != RaceStatus.OPEN && session.getStatus() != RaceStatus.RUNNING) {
            return;   // 이미 FINALIZING/COMPLETED/CANCELLED — no-op
        }
        List<Participation> participations = participationRepository.findBySessionId(sessionId);
        List<ParticipantClose> closes = buildCloses(participations);
        if (!SessionClosePolicy.shouldFinalize(closes, clock.instant(),
                session.getUploadDeadline())) {
            return;   // 아직 미업로드 STARTED 존재
        }
        doFinalize(session, participations, closes);
    }

    /** 마감 스케줄러 경로 — deadline 도달 시 무조건 확정(A9). 세션별 독립 트랜잭션(REQUIRES_NEW). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeByDeadline(Long sessionId) {
        Optional<RaceSession> found = sessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            return;
        }
        RaceSession session = found.get();
        if (session.isTerminal()) {
            return;   // COMPLETED/CANCELLED — no-op(재기동·중복 실행 내성)
        }
        List<Participation> participations = participationRepository.findBySessionId(sessionId);
        List<ParticipantClose> closes = buildCloses(participations);
        doFinalize(session, participations, closes);
    }

    private void doFinalize(RaceSession session, List<Participation> participations,
                            List<ParticipantClose> closes) {
        session.finalizeSession();                 // OPEN|RUNNING|FINALIZING → FINALIZING
        sessionRepository.save(session);

        List<ParticipantOutcome> outcomes = SessionClosePolicy.finalize(closes);
        Map<Long, Participation> byUser = participations.stream()
                .collect(Collectors.toMap(Participation::getUserId, Function.identity()));
        List<FinalizedParticipant> finalized = new ArrayList<>();
        for (ParticipantOutcome o : outcomes) {
            Participation p = byUser.get(o.userId());
            if (p != null) {
                p.finalizeTo(o.finalStatus());
                participationRepository.save(p);
            }
            finalized.add(new FinalizedParticipant(o.userId(), o.finalStatus().name(),
                    o.recordTimeS(), o.totalDistanceM()));
        }

        // Ranking이 동기 소비 → RaceResult/RankEntry 저장 → ResultFinalized → COMPLETED 전이.
        eventPublisher.publishEvent(
                new RaceCompleted(session.getId(), session.getCourseId(), finalized));
    }

    private List<ParticipantClose> buildCloses(List<Participation> participations) {
        Map<Long, TrackResult> tracks = trackResultQueryPort
                .findBySessionId(participations.isEmpty() ? -1L : participations.get(0).getSessionId())
                .stream()
                .collect(Collectors.toMap(TrackResult::userId, Function.identity()));
        List<ParticipantClose> closes = new ArrayList<>();
        for (Participation p : participations) {
            TrackResult tr = tracks.get(p.getUserId());
            boolean hasTrack = tr != null;
            boolean finished = hasTrack && tr.finished();
            Integer recordTimeS = hasTrack ? tr.recordTimeS() : null;
            Integer totalDistanceM = hasTrack ? tr.totalDistanceM() : null;
            closes.add(new ParticipantClose(p.getUserId(), p.getStatus(), hasTrack, finished,
                    recordTimeS, totalDistanceM));
        }
        return closes;
    }
}
