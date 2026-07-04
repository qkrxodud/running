/**
 * 애플리케이션 계층 — 유스케이스(포트 인터페이스 + 서비스), 트랜잭션 경계.
 *
 * <p>바운디드 컨텍스트: crew. 배치 A에서는 빈 골격만 고정한다(도메인 구현은 배치 B).
 */
package com.runningcrew.crew.application;
