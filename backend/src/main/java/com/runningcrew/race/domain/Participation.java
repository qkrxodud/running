package com.runningcrew.race.domain;

/**
 * 참가(설계 22 §3). 순수 도메인(ArchUnit R-1). register=REGISTERED 신규, start=STARTED(멱등).
 */
public class Participation {

    private final Long id;
    private final Long sessionId;
    private final Long userId;
    private ParticipationStatus status;

    public Participation(Long id, Long sessionId, Long userId, ParticipationStatus status) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.status = status;
    }

    /** opt-in 신청 — 신규 REGISTERED 행(J-1). */
    public static Participation register(Long sessionId, Long userId) {
        return new Participation(null, sessionId, userId, ParticipationStatus.REGISTERED);
    }

    /**
     * STARTED 신호(멱등). REGISTERED면 STARTED로, 이미 STARTED/FINISHED면 no-op.
     *
     * @return 이번 호출로 상태가 바뀌었으면 true(첫 STARTED 판별용)
     */
    public boolean start() {
        if (status == ParticipationStatus.STARTED || status == ParticipationStatus.FINISHED) {
            return false;   // 멱등 — 유실 무해
        }
        if (status == ParticipationStatus.REGISTERED) {
            this.status = ParticipationStatus.STARTED;
            return true;
        }
        // DNF/DNS/WITHDRAWN 등은 B2 능동 전이 대상 아님 — 방어적으로 거부
        throw new IllegalSessionTransitionException(RaceStatus.RUNNING, SessionCommand.START);
    }

    public boolean isStarted() {
        return status == ParticipationStatus.STARTED;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public ParticipationStatus getStatus() {
        return status;
    }
}
