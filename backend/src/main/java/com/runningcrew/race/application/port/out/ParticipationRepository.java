package com.runningcrew.race.application.port.out;

import com.runningcrew.race.domain.Participation;
import java.util.List;
import java.util.Optional;

/** Participation 쓰기 out-port. UQ(session_id,user_id)와 정합. */
public interface ParticipationRepository {

    Optional<Participation> findBySessionIdAndUserId(Long sessionId, Long userId);

    /** 마감 확정 시 세션의 전 참가자 최종화(A9). */
    List<Participation> findBySessionId(Long sessionId);

    Participation save(Participation participation);
}
