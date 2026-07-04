package com.runningcrew.race.adapter.out.persistence;

import com.runningcrew.race.application.port.out.SessionSchedulingPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** {@link SessionSchedulingPort} 구현 — 마감 도달 세션 id를 네이티브 SQL로 찾는다(A9). */
@Repository
public class SessionSchedulingAdapter implements SessionSchedulingPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Long> findDeadlineReachedSessionIds(Instant now) {
        List<?> rows = em.createNativeQuery(
                        "SELECT id FROM race_session "
                                + "WHERE status IN ('OPEN','RUNNING') AND upload_deadline <= ?1")
                .setParameter(1, now)
                .getResultList();
        return rows.stream().map(o -> ((Number) o).longValue()).toList();
    }
}
