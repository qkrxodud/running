package com.runningcrew.ranking.adapter.out.persistence;

import com.runningcrew.ranking.application.port.out.PriorPbPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * {@link PriorPbPort} 구현 — 유저×코스의 과거(다른 세션) 확정 결과 중 최소 완주기록을 네이티브 SQL로 찾는다.
 * rank_entry(ranking) + race_result + race_session(course_id, race 컨텍스트)을 조인하되 클래스 미참조(R-2).
 * {@code `rank`} 컬럼은 사용하지 않고 record_time_s만 비교한다.
 */
@Repository
public class PriorPbAdapter implements PriorPbPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Integer findPriorPbTimeS(Long courseId, Long userId, Long excludeSessionId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT MIN(re.record_time_s) FROM rank_entry re "
                                + "JOIN race_result rr ON rr.id = re.result_id "
                                + "JOIN race_session rs ON rs.id = rr.session_id "
                                + "WHERE rs.course_id = ?1 AND re.user_id = ?2 "
                                + "AND rr.session_id <> ?3 AND re.record_time_s IS NOT NULL")
                .setParameter(1, courseId)
                .setParameter(2, userId)
                .setParameter(3, excludeSessionId)
                .getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        return ((Number) rows.get(0)).intValue();
    }
}
