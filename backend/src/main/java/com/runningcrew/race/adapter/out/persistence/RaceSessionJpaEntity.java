package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.domain.RaceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code race_session} 테이블 매핑(설계 §2.7). enum은 STRING 고정. */
@Entity
@Table(name = "race_session")
public class RaceSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "crew_id", nullable = false)
    private Long crewId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "upload_deadline", nullable = false)
    private Instant uploadDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RaceStatus status;

    @Column(name = "replay_notified_at")
    private Instant replayNotifiedAt;

    protected RaceSessionJpaEntity() {
    }

    public RaceSessionJpaEntity(Long id, Long crewId, Long courseId, Instant scheduledAt,
                                Instant uploadDeadline, RaceStatus status, Instant replayNotifiedAt) {
        this.id = id;
        this.crewId = crewId;
        this.courseId = courseId;
        this.scheduledAt = scheduledAt;
        this.uploadDeadline = uploadDeadline;
        this.status = status;
        this.replayNotifiedAt = replayNotifiedAt;
    }

    public void updateStatus(RaceStatus status) {
        this.status = status;
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
