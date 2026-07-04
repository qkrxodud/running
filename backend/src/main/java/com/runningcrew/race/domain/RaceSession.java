package com.runningcrew.race.domain;

import java.time.Instant;

/**
 * RaceSession 애그리거트 루트(설계 22 §2). 순수 도메인(ArchUnit R-1) — 상태 전이 가드는
 * {@link RaceSessionPolicy}에 위임한다. 참가자 컬렉션은 조회 모델에서 조인(애그리거트 밖).
 *
 * <p>불변식(RS-B*): 전이는 매트릭스만(RS-B1), {@code upload_deadline > scheduled_at}(RS-B4).
 */
public class RaceSession {

    private final Long id;
    private final Long crewId;
    private final Long courseId;
    private final Instant scheduledAt;
    private final Instant uploadDeadline;
    private RaceStatus status;
    private final Instant replayNotifiedAt;   // M2 — 현재 항상 null

    public RaceSession(Long id, Long crewId, Long courseId, Instant scheduledAt,
                       Instant uploadDeadline, RaceStatus status, Instant replayNotifiedAt) {
        this.id = id;
        this.crewId = crewId;
        this.courseId = courseId;
        this.scheduledAt = scheduledAt;
        this.uploadDeadline = uploadDeadline;
        this.status = status;
        this.replayNotifiedAt = replayNotifiedAt;
    }

    /**
     * 세션 생성 — status=DRAFT(발행 전 준비). {@code upload_deadline > scheduled_at} 강제(RS-B4).
     * "예정+12h"는 앱레이어 UX 기본값이지 도메인 규칙이 아니다.
     *
     * @throws InvalidRaceSessionException 시각 누락 또는 마감이 예정 이전
     */
    public static RaceSession create(Long crewId, Long courseId, Instant scheduledAt,
                                     Instant uploadDeadline) {
        if (scheduledAt == null || uploadDeadline == null) {
            throw new InvalidRaceSessionException("scheduled_at·upload_deadline은 필수입니다.");
        }
        if (!uploadDeadline.isAfter(scheduledAt)) {
            throw new InvalidRaceSessionException("upload_deadline은 scheduled_at보다 이후여야 합니다.");
        }
        return new RaceSession(null, crewId, courseId, scheduledAt, uploadDeadline,
                RaceStatus.DRAFT, null);
    }

    /** DRAFT→OPEN(발행). 코스 참조가 발행으로 잠긴다(J-2). */
    public void open() {
        this.status = RaceSessionPolicy.apply(status, SessionCommand.OPEN);
    }

    /** DRAFT|OPEN|RUNNING→CANCELLED. 순위·보상 미생성. */
    public void cancel() {
        this.status = RaceSessionPolicy.apply(status, SessionCommand.CANCEL);
    }

    /** OPEN에서 register 가능한지 가드(상태 불변). */
    public void ensureRegisterable() {
        RaceSessionPolicy.apply(status, SessionCommand.REGISTER);
    }

    /** 첫 STARTED가 OPEN→RUNNING을 유발(멱등 — RUNNING이면 유지). */
    public void onStartSignal() {
        this.status = RaceSessionPolicy.apply(status, SessionCommand.START);
    }

    /** OPEN|RUNNING|FINALIZING→FINALIZING(마감 진입, 재진입 멱등 — A9). */
    public void finalizeSession() {
        this.status = RaceSessionPolicy.apply(status, SessionCommand.FINALIZE);
    }

    /** FINALIZING→COMPLETED(결과 확정 후 — ResultFinalized 소비). */
    public void complete() {
        this.status = RaceSessionPolicy.apply(status, SessionCommand.COMPLETE);
    }

    public boolean isTerminal() {
        return status == RaceStatus.COMPLETED || status == RaceStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getCrewId() {
        return crewId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getUploadDeadline() {
        return uploadDeadline;
    }

    public RaceStatus getStatus() {
        return status;
    }

    public Instant getReplayNotifiedAt() {
        return replayNotifiedAt;
    }
}
