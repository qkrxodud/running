package com.runningcrew.replay.application.port.out;

import java.util.List;
import java.util.Optional;

/**
 * 스냅샷 생성 소스 out-port(RP-1) — replay는 track_payload(refined)의 <b>명시적 소비자</b>. 이 포트만
 * track_payload를 native 접근하고, 순위/결과/히스토리 조회 어댑터엔 여전히 미주입(격리 유지). Tracking/
 * Ranking 리포지토리·서비스 직접 호출 0(RP-2 — 이벤트/전용 포트로만).
 */
public interface ReplaySourcePort {

    /** 세션의 생성 소스(코스 + 참가자별 refined). 세션·트랙 부재면 empty. */
    Optional<GenerationSource> loadGenerationSource(Long sessionId);

    /** 코스 배경 + 참가자 refined 트랙(스냅샷 course·participants 재료). */
    record GenerationSource(int courseDistanceM, String coursePolyline,
                            double startLat, double startLng, double finishLat, double finishLng,
                            List<ParticipantSource> participants) {
    }

    /**
     * 참가자 트랙 소스. {@code refinedPayloadJson}은 track_payload.refined_payload(폴리라인·timestamps·
     * gps_gaps·segments 내장) — 서비스가 파싱해 병합 입력으로 변환. DNS(트랙 없음)는 부재.
     */
    record ParticipantSource(long userId, long startedAtMillis, Long finishedAtMillis,
                             String finishStatus, String refinedPayloadJson) {
    }
}
