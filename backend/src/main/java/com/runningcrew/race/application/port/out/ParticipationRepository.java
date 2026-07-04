package com.runningcrew.race.application.port.out;

import com.runningcrew.race.domain.Participation;
import java.util.Optional;

/** Participation 쓰기 out-port. UQ(session_id,user_id)와 정합. */
public interface ParticipationRepository {

    Optional<Participation> findBySessionIdAndUserId(Long sessionId, Long userId);

    Participation save(Participation participation);
}
