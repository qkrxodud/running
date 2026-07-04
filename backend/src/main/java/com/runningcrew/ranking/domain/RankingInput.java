package com.runningcrew.ranking.domain;

/**
 * 순위 산정 입력(참가자 1인) — 순수 함수 {@link RankingPolicy} 인자.
 *
 * @param userId        참가자
 * @param status        최종 상태(FINISHED/DNF/DNS)
 * @param recordTimeS   완주 기록(초). FINISHED만 유효, 그 외 null
 * @param priorPbTimeS  과거(다른 세션) 동일 코스 최소 완주기록(초). 없으면 null — PB 판정용(애플리케이션이 주입)
 */
public record RankingInput(Long userId, ResultStatus status, Integer recordTimeS,
                           Integer priorPbTimeS) {
}
