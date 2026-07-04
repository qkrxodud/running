package com.runningcrew.race.application.port.out;

import java.util.List;

/**
 * 세션의 트랙 판정 결과 조회 out-port — track_record <b>요약만</b> 읽는다(track_payload 조인 0건, TR-3).
 * race 컨텍스트가 tracking 클래스를 참조하지 않도록 어댑터가 네이티브 SQL로 구현한다(ArchUnit R-2).
 */
public interface TrackResultQueryPort {

    List<TrackResult> findBySessionId(Long sessionId);

    /**
     * @param userId         참가자
     * @param finished       finished_at 존재(완주 판정 결과)
     * @param recordTimeS    완주 기록(초, null 가능)
     * @param totalDistanceM 정제 거리(m, null 가능)
     */
    record TrackResult(Long userId, boolean finished, Integer recordTimeS, Integer totalDistanceM) {
    }
}
