/**
 * 아웃바운드 어댑터 — JPA 엔티티, 리포지토리 구현.
 *
 * <p>바운디드 컨텍스트: race. 배치 A에서는 빈 골격만 고정한다(도메인 구현은 배치 B).
 */
package com.runningcrew.race.adapter.out.persistence;
