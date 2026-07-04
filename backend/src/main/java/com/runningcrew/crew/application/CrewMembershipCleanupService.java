package com.runningcrew.crew.application;

import com.runningcrew.crew.application.port.out.CrewRepository;
import com.runningcrew.crew.domain.Crew;
import com.runningcrew.user.domain.event.UserWithdrawn;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 컨텍스트 간 이벤트 소비: {@link UserWithdrawn} → 크루 멤버십 정리·승계(설계 §3.3, 불변식 C-B6).
 *
 * <p><b>동기 @EventListener(같은 트랜잭션)</b> — 탈퇴와 승계가 원자적이어야 "크루장 없는 크루"가
 * 관측되지 않는다. user→crew 직접 호출 없이 이벤트 경유만(ArchUnit R-2: user.domain.event만 import).
 */
@Service
public class CrewMembershipCleanupService {

    private final CrewRepository crewRepository;
    private final Clock clock;

    public CrewMembershipCleanupService(CrewRepository crewRepository, Clock clock) {
        this.crewRepository = crewRepository;
        this.clock = clock;
    }

    @EventListener
    public void on(UserWithdrawn event) {
        Instant now = clock.instant();
        List<Crew> crews = crewRepository.findAllByActiveMemberUserId(event.userId());
        for (Crew crew : crews) {
            crew.handleMemberWithdrawn(event.userId(), now);
            crewRepository.save(crew);
        }
    }
}
