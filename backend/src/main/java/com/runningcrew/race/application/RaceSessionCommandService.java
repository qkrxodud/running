package com.runningcrew.race.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.race.application.port.out.CourseQueryPort;
import com.runningcrew.race.application.port.out.CrewAccessPort;
import com.runningcrew.race.application.port.out.CrewAccessPort.CrewRef;
import com.runningcrew.race.application.port.out.ParticipationRepository;
import com.runningcrew.race.application.port.out.RaceSessionRepository;
import com.runningcrew.race.application.port.out.SessionQueryPort;
import com.runningcrew.race.application.view.SessionDetailView;
import com.runningcrew.race.domain.IllegalSessionTransitionException;
import com.runningcrew.race.domain.InvalidRaceSessionException;
import com.runningcrew.race.domain.Participation;
import com.runningcrew.race.domain.RaceSession;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션·참가 명령 유스케이스(session-api.md §1·§4~§7). 상태머신·권한·멱등을 조립한다.
 *
 * <ul>
 *   <li>create/open/cancel: <b>크루장 전용</b> + 크루 ACTIVE(CLOSED → CREW_CLOSED).
 *   <li>register: OPEN 세션·ACTIVE 멤버 본인·멱등(no-op).
 *   <li>start: 선 register 필요(부재 409), OPEN/RUNNING만, 멱등, 첫 STARTED가 OPEN→RUNNING.
 * </ul>
 */
@Service
public class RaceSessionCommandService {

    private final RaceSessionRepository sessionRepository;
    private final ParticipationRepository participationRepository;
    private final CourseQueryPort courseQueryPort;
    private final CrewAccessPort crewAccessPort;
    private final SessionQueryPort sessionQueryPort;

    public RaceSessionCommandService(RaceSessionRepository sessionRepository,
                                     ParticipationRepository participationRepository,
                                     CourseQueryPort courseQueryPort,
                                     CrewAccessPort crewAccessPort,
                                     SessionQueryPort sessionQueryPort) {
        this.sessionRepository = sessionRepository;
        this.participationRepository = participationRepository;
        this.courseQueryPort = courseQueryPort;
        this.crewAccessPort = crewAccessPort;
        this.sessionQueryPort = sessionQueryPort;
    }

    /** 크루장 전용 세션 생성 → DRAFT. 코스는 같은 크루 소유여야 한다(RS-B5). */
    @Transactional
    public SessionDetailView createSession(Long userId, Long crewId, CreateSessionCommand command) {
        CrewRef crew = requireLeaderOnActiveCrew(crewId, userId);
        Long courseCrewId = courseQueryPort.findCrewId(command.courseId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));   // 코스 없음
        if (!courseCrewId.equals(crew.crewId())) {
            throw new ApiException(ErrorCode.SESSION_STATE_INVALID, "코스가 이 크루 소속이 아닙니다.");
        }
        RaceSession session;
        try {
            session = RaceSession.create(crewId, command.courseId(),
                    command.scheduledAt(), command.uploadDeadline());
        } catch (InvalidRaceSessionException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
        RaceSession saved = sessionRepository.save(session);
        return detail(saved.getId());
    }

    /** DRAFT→OPEN 발행(크루장 전용). */
    @Transactional
    public SessionDetailView openSession(Long userId, Long sessionId) {
        RaceSession session = requireSession(sessionId);
        requireLeaderOnActiveCrew(session.getCrewId(), userId);
        applyTransition(session::open);
        sessionRepository.save(session);
        return detail(sessionId);
    }

    /** DRAFT|OPEN|RUNNING→CANCELLED(크루장 전용). 순위·보상 미생성, participation 미변경. */
    @Transactional
    public SessionDetailView cancelSession(Long userId, Long sessionId) {
        RaceSession session = requireSession(sessionId);
        requireLeaderOnActiveCrew(session.getCrewId(), userId);
        applyTransition(session::cancel);
        sessionRepository.save(session);
        return detail(sessionId);
    }

    /** 참가 신청(opt-in) — OPEN 세션·ACTIVE 멤버 본인·멱등. */
    @Transactional
    public SessionDetailView register(Long userId, Long sessionId) {
        RaceSession session = requireSession(sessionId);
        if (!crewAccessPort.isActiveMember(session.getCrewId(), userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 크루 비멤버
        }
        applyTransition(session::ensureRegisterable);   // OPEN 아니면 409
        Optional<Participation> existing =
                participationRepository.findBySessionIdAndUserId(sessionId, userId);
        if (existing.isEmpty()) {
            participationRepository.save(Participation.register(sessionId, userId));
        }
        // 이미 REGISTERED/STARTED면 멱등 no-op
        return detail(sessionId);
    }

    /** 시작 신호(STARTED, 멱등) — 선 register 필요. 첫 STARTED가 OPEN→RUNNING을 유발. */
    @Transactional
    public SessionDetailView start(Long userId, Long sessionId) {
        RaceSession session = requireSession(sessionId);
        if (!crewAccessPort.isActiveMember(session.getCrewId(), userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 참가자 아님(권한)
        }
        Participation participation = participationRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_STATE_INVALID,
                        "선 참가 신청이 필요합니다."));   // participation 부재
        applyTransition(session::onStartSignal);   // OPEN/RUNNING 아니면 409
        applyTransition(participation::start);      // 멱등
        participationRepository.save(participation);
        sessionRepository.save(session);
        return detail(sessionId);
    }

    private CrewRef requireLeaderOnActiveCrew(Long crewId, Long userId) {
        CrewRef crew = crewAccessPort.findCrew(crewId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!crew.isLeader(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 크루장 아님(RS-B2)
        }
        if (crew.closed()) {
            throw new ApiException(ErrorCode.CREW_CLOSED);   // RS-B3
        }
        return crew;
    }

    private RaceSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private void applyTransition(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalSessionTransitionException e) {
            throw new ApiException(ErrorCode.SESSION_STATE_INVALID);
        }
    }

    private SessionDetailView detail(Long sessionId) {
        return sessionQueryPort.findDetail(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
