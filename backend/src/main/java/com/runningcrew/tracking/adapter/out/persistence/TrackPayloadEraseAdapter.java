package com.runningcrew.tracking.adapter.out.persistence;

import com.runningcrew.tracking.application.port.out.TrackDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link TrackDataEraser} 구현 — track_payload 네이티브 삭제. B1은 아직 tracking 엔티티가 없어
 * 요약 테이블(track_record)을 참조하는 서브쿼리로 대상 payload를 찾는다(설계 §1.3 step5).
 *
 * <p>탈퇴 트랜잭션(UserWithdrawn 동기 소비) 안에서 실행된다.
 */
@Repository
public class TrackPayloadEraseAdapter implements TrackDataEraser {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void eraseByUserId(Long userId) {
        em.createNativeQuery(
                        "DELETE FROM track_payload WHERE track_record_id IN "
                                + "(SELECT id FROM track_record WHERE user_id = ?1)")
                .setParameter(1, userId)
                .executeUpdate();
    }
}
