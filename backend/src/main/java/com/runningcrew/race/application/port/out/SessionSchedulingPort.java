package com.runningcrew.race.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * 마감 스케줄러 지원 out-port — upload_deadline 도달한 미확정 세션 id를 찾는다(A9). 네이티브 SQL.
 */
public interface SessionSchedulingPort {

    /** {@code status ∈ {OPEN,RUNNING} AND upload_deadline ≤ now} 세션 id 목록. */
    List<Long> findDeadlineReachedSessionIds(Instant now);
}
