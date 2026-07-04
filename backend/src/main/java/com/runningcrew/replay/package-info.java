/**
 * 리플레이 프로젝션 계층 — 애그리거트 없음. ReplayGenerationService + SnapshotRepository 자리.
 *
 * <p>ResultFinalized 커밋 후 비동기(AFTER_COMMIT + Async)로 스냅샷 생성. 배치 A는 골격만.
 */
package com.runningcrew.replay;
