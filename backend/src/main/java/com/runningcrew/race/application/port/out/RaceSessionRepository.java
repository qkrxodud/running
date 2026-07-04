package com.runningcrew.race.application.port.out;

import com.runningcrew.race.domain.RaceSession;
import java.util.Optional;

/** RaceSession 애그리거트 쓰기 out-port. */
public interface RaceSessionRepository {

    RaceSession save(RaceSession session);

    Optional<RaceSession> findById(Long id);
}
