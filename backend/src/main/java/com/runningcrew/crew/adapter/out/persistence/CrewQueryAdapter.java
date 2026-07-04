package com.runningcrew.crew.adapter.out.persistence;

import com.runningcrew.crew.application.port.out.CrewQueryPort;
import com.runningcrew.crew.application.view.CrewDetailView;
import com.runningcrew.crew.application.view.CrewMemberView;
import com.runningcrew.crew.application.view.CrewSummaryView;
import com.runningcrew.crew.domain.CrewMemberStatus;
import com.runningcrew.crew.domain.CrewRole;
import com.runningcrew.crew.domain.CrewStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * {@link CrewQueryPort} 구현(읽기 모델). 멤버 닉네임은 {@code user} 테이블과의 <b>네이티브 SQL 조인</b>으로
 * 해석한다 — user 도메인 클래스를 참조하지 않으므로 컨텍스트 간 클래스 의존이 없다(ArchUnit R-2 정합, 설계 §3.3).
 */
@Repository
public class CrewQueryAdapter implements CrewQueryPort {

    @PersistenceContext
    private EntityManager em;

    private final CrewJpaRepository crewJpaRepository;
    private final CrewMemberJpaRepository crewMemberJpaRepository;

    public CrewQueryAdapter(CrewJpaRepository crewJpaRepository,
                            CrewMemberJpaRepository crewMemberJpaRepository) {
        this.crewJpaRepository = crewJpaRepository;
        this.crewMemberJpaRepository = crewMemberJpaRepository;
    }

    @Override
    public Optional<CrewDetailView> findDetail(Long crewId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT c.id, c.name, c.status, c.created_at, c.leader_id, lu.nickname "
                                + "FROM crew c JOIN `user` lu ON lu.id = c.leader_id WHERE c.id = ?1")
                .setParameter(1, crewId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = (Object[]) rows.get(0);
        Long id = ((Number) r[0]).longValue();
        String name = (String) r[1];
        CrewStatus status = CrewStatus.valueOf((String) r[2]);
        Instant createdAt = toInstant(r[3]);
        Long leaderUserId = ((Number) r[4]).longValue();
        String leaderNickname = (String) r[5];

        List<CrewMemberView> members = findActiveMembers(crewId);
        return Optional.of(new CrewDetailView(id, name, status, leaderUserId, leaderNickname,
                createdAt, members));
    }

    private List<CrewMemberView> findActiveMembers(Long crewId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT cm.user_id, u.nickname, cm.role, cm.joined_at "
                                + "FROM crew_member cm JOIN `user` u ON u.id = cm.user_id "
                                + "WHERE cm.crew_id = ?1 AND cm.status = 'ACTIVE' "
                                + "ORDER BY cm.joined_at ASC, cm.id ASC")
                .setParameter(1, crewId)
                .getResultList();
        return rows.stream().map(o -> {
            Object[] r = (Object[]) o;
            return new CrewMemberView(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    CrewRole.valueOf((String) r[2]),
                    toInstant(r[3]));
        }).toList();
    }

    @Override
    public boolean isActiveMember(Long crewId, Long userId) {
        return crewMemberJpaRepository.existsByCrew_IdAndUserIdAndStatus(
                crewId, userId, CrewMemberStatus.ACTIVE);
    }

    @Override
    public Page<CrewSummaryView> findMyCrews(Long userId, Pageable pageable) {
        return crewJpaRepository.findMyCrews(userId, pageable);
    }

    /** 네이티브 TIMESTAMP 스칼라를 UTC Instant로. 드라이버가 Timestamp/LocalDateTime/Instant 중 무엇을 줘도 대응. */
    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);   // 세션 타임존 UTC 전제
        }
        throw new IllegalStateException("지원하지 않는 시각 타입: " + value.getClass());
    }
}
