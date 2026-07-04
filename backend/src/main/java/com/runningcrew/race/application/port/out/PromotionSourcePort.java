package com.runningcrew.race.application.port.out;

import java.util.Optional;

/**
 * 코스 승격 소스 트랙 조회 out-port(course-api §4 / C6). 승격은 refined 경로를 읽어야 하므로 <b>명시적
 * payload 소비자</b>(PR-6) — 이 포트만 track_payload를 접근하고, 순위·결과·히스토리 <b>조회 어댑터엔 주입
 * 금지</b>(TR-3 격리 유지, 승격은 예외 경로). race 컨텍스트는 tracking 클래스 미참조(R-2 — 네이티브 SQL).
 */
public interface PromotionSourcePort {

    Optional<PromotionSource> find(Long trackRecordId);

    /**
     * 승격 자격·재료. {@code refinedPolyline}은 refined_payload에서 추출한 1e5 인코딩 경로(서버가 이걸로
     * distance·start/finish 재확정). 트랙 미존재면 {@link Optional#empty()}.
     *
     * @param ownerUserId    트랙 소유자(본인 검증 — 타인이면 403)
     * @param finished       finish_status=FINISHED 여부(DNF면 승격 불가)
     * @param totalDistanceM 정제 후 거리(거리 하한 판정)
     * @param refinedPolyline refined 경로(1e5). 정제 경로 부재 시 null
     */
    record PromotionSource(Long ownerUserId, boolean finished, int totalDistanceM,
                           String refinedPolyline) {
    }
}
