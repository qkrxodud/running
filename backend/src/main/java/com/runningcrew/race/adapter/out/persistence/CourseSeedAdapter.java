package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.CourseSeedPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link CourseSeedPort} 구현 — crew/course 네이티브 SQL(컨텍스트 클래스 미참조 — R-2). */
@Repository
public class CourseSeedAdapter implements CourseSeedPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<SeedTargetCrew> findSeedTargetCrew() {
        List<?> rows = em.createNativeQuery(
                        "SELECT id, leader_id FROM crew WHERE status = 'ACTIVE' ORDER BY id ASC LIMIT 1")
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = (Object[]) rows.get(0);
        return Optional.of(new SeedTargetCrew(
                ((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
    }

    @Override
    public boolean courseExists(Long crewId, String name) {
        Number count = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM course WHERE crew_id = ?1 AND name = ?2")
                .setParameter(1, crewId)
                .setParameter(2, name)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
