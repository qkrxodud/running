package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.CrewAccessPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link CrewAccessPort} 구현 — crew/crew_member 테이블 <b>네이티브 SQL</b>로 크루장·CLOSED·ACTIVE 멤버
 * 여부만 조회한다. 크루 컨텍스트의 클래스를 참조하지 않으므로 컨텍스트 간 클래스 의존이 없다(ArchUnit R-2).
 */
@Repository
public class CrewAccessAdapter implements CrewAccessPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<CrewRef> findCrew(Long crewId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT id, leader_id, status FROM crew WHERE id = ?1")
                .setParameter(1, crewId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = (Object[]) rows.get(0);
        Long id = ((Number) r[0]).longValue();
        Long leaderId = ((Number) r[1]).longValue();
        boolean closed = "CLOSED".equals(r[2]);
        return Optional.of(new CrewRef(id, leaderId, closed));
    }

    @Override
    public boolean isActiveMember(Long crewId, Long userId) {
        Number count = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM crew_member "
                                + "WHERE crew_id = ?1 AND user_id = ?2 AND status = 'ACTIVE'")
                .setParameter(1, crewId)
                .setParameter(2, userId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
