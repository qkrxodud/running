package com.runningcrew.crew.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.crew.application.port.out.CrewQueryPort;
import com.runningcrew.crew.application.port.out.CrewRepository;
import com.runningcrew.crew.application.port.out.InviteCodeRepository;
import com.runningcrew.crew.application.view.CrewDetailView;
import com.runningcrew.crew.domain.AlreadyJoinedException;
import com.runningcrew.crew.domain.Crew;
import com.runningcrew.crew.domain.CrewClosedException;
import com.runningcrew.crew.domain.InvalidCrewNameException;
import com.runningcrew.crew.domain.InviteCode;
import com.runningcrew.crew.domain.event.CrewMemberJoined;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 명령 유스케이스(계약 crew-api.md §1·§4·§5): 생성, 초대 코드 생성, 코드 참가.
 */
@Service
public class CrewCommandService {

    private final CrewRepository crewRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final CrewQueryPort crewQueryPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CrewCommandService(CrewRepository crewRepository,
                              InviteCodeRepository inviteCodeRepository,
                              InviteCodeGenerator inviteCodeGenerator,
                              CrewQueryPort crewQueryPort,
                              ApplicationEventPublisher eventPublisher,
                              Clock clock) {
        this.crewRepository = crewRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.crewQueryPort = crewQueryPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 크루 생성 — 생성자가 LEADER. 201 CrewDetail 반환. */
    @Transactional
    public CrewDetailView createCrew(Long userId, String rawName) {
        Crew crew;
        try {
            crew = Crew.create(rawName, userId, clock.instant());
        } catch (InvalidCrewNameException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
        Crew saved = crewRepository.save(crew);
        return crewQueryPort.findDetail(saved.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** 초대 코드 생성(크루장 전용). 201 InviteCode 반환. */
    @Transactional
    public InviteCode createInviteCode(Long userId, Long crewId, int maxUses, int expiresInHours) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!crew.getLeaderId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 크루장 아님
        }
        if (crew.isClosed()) {
            throw new ApiException(ErrorCode.CREW_CLOSED);
        }
        Instant expiresAt = clock.instant().plus(expiresInHours, ChronoUnit.HOURS);
        String code = inviteCodeGenerator.generateUnique();
        return inviteCodeRepository.save(InviteCode.create(code, crewId, expiresAt, maxUses));
    }

    /**
     * 초대 코드 참가(설계 §3.2). 검증 순서: 코드 존재→만료→소진→크루 ACTIVE→멤버십. 코드 행 잠금으로 직렬화.
     */
    @Transactional
    public CrewDetailView joinCrew(Long userId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        InviteCode invite = inviteCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_CODE_INVALID));   // 404
        Instant now = clock.instant();
        if (invite.isExpired(now)) {
            throw new ApiException(ErrorCode.INVITE_CODE_EXPIRED);   // 409
        }
        if (invite.isExhausted()) {
            throw new ApiException(ErrorCode.INVITE_CODE_EXHAUSTED); // 409
        }
        Crew crew = crewRepository.findById(invite.getCrewId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        try {
            crew.join(userId, now);
        } catch (CrewClosedException e) {
            throw new ApiException(ErrorCode.CREW_CLOSED);
        } catch (AlreadyJoinedException e) {
            throw new ApiException(ErrorCode.ALREADY_JOINED);
        }
        invite.incrementUse();
        inviteCodeRepository.save(invite);
        crewRepository.save(crew);
        // O-1: 인앱 갈음 — 소비자 없이 발행만(확장 지점 보존). 로그 소비자는 CrewMemberJoinedLogListener.
        eventPublisher.publishEvent(new CrewMemberJoined(crew.getId(), userId));
        return crewQueryPort.findDetail(crew.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
