/**
 * 공용 계층 — 공용 VO(좌표·폴리라인), 이벤트 발행 지원, 시각/직렬화 설정, 공통 오류 응답,
 * 페이지네이션 래퍼. 특정 바운디드 컨텍스트에 속하지 않는 횡단 관심사와 운영 메타를 둔다.
 *
 * <p>배치 A 실체: {@code TimeConfig}, {@code ApiError} + 전역 예외 처리, {@code PageResponse},
 * 그리고 운영 메타 엔드포인트 {@code app-version}(어느 도메인에도 속하지 않음).
 */
package com.runningcrew.common;
