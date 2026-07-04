package com.runningcrew.common.error;

/**
 * 통일 오류 응답 shape(계약 conventions.md §4): {@code { "code": ..., "message": ... }}.
 *
 * @param code    기계 판독용 상수(UPPER_SNAKE). 클라이언트 분기는 이 값으로만 한다.
 * @param message 사람용 메시지(한국어). 표시 문구는 클라가 code로 자체 로케일 대응 가능.
 */
public record ApiError(String code, String message) {}
