package com.runningcrew.ranking.application.port.out;

import com.runningcrew.ranking.application.view.ResultView;
import java.util.Optional;

/**
 * 결과·순위 조회 out-port(track-api §3). track_record <b>요약</b>·rank_entry·participation·user·course를
 * 네이티브 SQL로 조인한다 — <b>track_payload 조인 0건</b>(TR-3, A7/A8 후 QA 재검증 대상). race 클래스 미참조(R-2).
 */
public interface LoadResultPort {

    boolean sessionExists(Long sessionId);

    boolean isCrewMember(Long sessionId, Long userId);

    /** 결과 확정(race_result 존재) 시 순위표. 미확정이면 empty(→ 409 RESULT_NOT_READY). */
    Optional<ResultView> findResult(Long sessionId);
}
