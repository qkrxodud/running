package com.runningcrew.ranking.domain;

/**
 * 결과 최종 상태(순위 산정 입력·출력). Participation 최종값의 부분집합(계약 track-api §3 entries.status).
 * ranking 로컬 표현 — race {@code ParticipationStatus}를 참조하지 않는다(R-2).
 */
public enum ResultStatus {
    FINISHED,
    DNF,
    DNS
}
