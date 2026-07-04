/**
 * 앱 최소 버전 운영 메타(계약 app-version.md, 설계문서 M-7: common 소속).
 *
 * <p>어느 바운디드 컨텍스트에도 속하지 않는 운영 메타 → common에 읽기 전용 어댑터로 둔다.
 * 첫 계약 구현 사례: GET /api/v1/app-version.
 */
package com.runningcrew.common.appversion;
