package com.runningcrew.ranking.application.port.out;

/**
 * 과거 PB 조회 out-port(설계 42 §5.2) — 유저×코스의 <b>다른(과거) 세션</b> 확정 결과 중 최소 완주기록.
 * rank_entry(ranking) + race_session(course_id)을 네이티브 SQL로 조인한다(race 클래스 미참조 — R-2).
 */
public interface PriorPbPort {

    /** 없으면 null(첫 완주는 항상 PB). {@code excludeSessionId}는 현재 확정 중인 세션(자기 제외). */
    Integer findPriorPbTimeS(Long courseId, Long userId, Long excludeSessionId);
}
