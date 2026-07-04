package com.runningcrew.tracking.domain;

/**
 * 트랙 페이로드 규칙 위반(순수 도메인 예외 — 프레임워크 무관). 폴리라인 디코딩 실패·시간 정합성 위반 등.
 * 애플리케이션 어댑터가 계약 오류코드(TRACK_PAYLOAD_INVALID 등)로 옮긴다.
 */
public class InvalidTrackPayloadException extends RuntimeException {

    public InvalidTrackPayloadException(String message) {
        super(message);
    }
}
