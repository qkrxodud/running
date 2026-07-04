package com.runningcrew.ranking.domain;

/**
 * 순위 산정 출력(참가자 1인). rank는 완주자만(동률 공동순위·다음 건너뜀), DNF/DNS는 null.
 *
 * @param userId      참가자
 * @param status      최종 상태
 * @param rank        순위(완주자만, null 가능)
 * @param recordTimeS 완주 기록(초, null 가능)
 * @param isPb        개인 최고 기록 여부(완주만 true 가능)
 */
public record RankedEntry(Long userId, ResultStatus status, Integer rank, Integer recordTimeS,
                          boolean isPb) {
}
