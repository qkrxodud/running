package com.runningcrew.ranking.application.port.out;

import com.runningcrew.ranking.domain.RankedEntry;
import java.time.Instant;
import java.util.List;

/**
 * 순위 결과 저장 out-port — race_result(세션당 1) + rank_entry(참가자별). {@code rank}는 예약어라 JPA
 * 전역 인용으로 안전 매핑(RE-1, R-003 이월). 세션당 1결과(UQ) — 재확정 충돌은 상위에서 idempotent 가드.
 *
 * @return 생성된 race_result id
 */
public interface SaveRankingResultPort {

    Long save(Long sessionId, Instant finalizedAt, List<RankedEntry> entries);
}
