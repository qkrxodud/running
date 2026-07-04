# 회귀 레지스트리 (Regression Registry)

발견된 모든 버그의 영구 장부. **수정만 하고 끝내면 같은 버그는 반드시 돌아온다** — 모든 항목은 재발 방지 장치(재현 테스트 또는 자동 체크)가 연결되어야 CLOSED 처리된다.

## 규칙

1. 차단급/버그 발견 시 이 표에 즉시 등록한다 (발견자: qa, test-engineer, 사용자 누구든).
2. 수정 완료 ≠ 종결. **재발 방지 장치 열이 채워져야 종결**이다. 장치는 우선순위 순으로: ① 재현 테스트(골든/유닛) ② QA 상시 점검 항목(qa-integration 스킬에 추가) ③ 스킬/에이전트 규칙 수정(같은 실수를 만드는 절차 자체를 교정).
3. 같은 유형의 버그가 2회 이상 재발하면 장치 등급을 올린다 (체크리스트 → 테스트, 테스트 → 절차 교정).
4. 항목은 삭제하지 않는다 — CLOSED로 남겨 패턴 분석의 원료로 쓴다.

## 장부

| ID | 날짜 | 증상 | 원인 | 재발 방지 장치 | 상태 |
|----|------|------|------|---------------|------|
| R-001 | (예시) | 앱이 서버 enum `DNF`를 소문자로 파싱해 크래시 | 계약에 enum 값 케이스 미명시 | `app/test/participation_status_test.dart` + 계약 템플릿에 enum 값 집합 필수화 | 예시 |
| R-002 | 2026-07-04 | 플랫폼 격리 가드(`app/test/core/no_platform_imports_test.dart`)가 denylist라 목록 밖 패키지(예: `package:dio`, `package:flutter_riverpod`)가 `lib/core/`로 유입돼도 CI 통과. 현재 실제 위반 0건이나 배치 B의 dio HTTP 배선 시 C-5·순수함수 경계가 무방비. | 가드가 고정 금지목록 4개만 검사(allowlist 아님). `package:flutter/`는 슬래시 포함이라 `package:flutter_riverpod`도 미검출. | 가드 allowlist 전환 (no_platform_imports_test.dart) — `lib/core/` 허용 import 는 `dart:*` + 상대경로만, `package:` 전부 실패. 캐너리(`package:dio` 임시 삽입)로 검출 검증 완료. **QA 2차 재검증(2026-07-04): allowlist 전환 확인 + 독립 캐너리 재실행으로 검출 동작 확인 — CLOSED 유지 타당.** | CLOSED |
| R-004 | 2026-07-04 | 실기기/에뮬레이터 로그인 시 NETWORK_ERROR. 조사 중 **main 매니페스트에 INTERNET 권한 부재** 발견 — debug는 Flutter 기본 debug 오버레이가 권한을 줘서 가려지고, release 빌드에서만 네트워크 전면 차단되는 지뢰였음 | Flutter 스캐폴드 기본값(main에 INTERNET 없음)을 배치 A~B2 동안 아무도 의심 안 함. cleartext(dev http) 미허용도 중첩 | main 매니페스트 INTERNET 추가 + debug 오버레이 usesCleartextTraffic(디버그 한정). qa-integration 스킬 상시 점검에 "릴리즈 매니페스트 권한·네트워크 스모크" 항목 승격 | CLOSED |
| R-003 | 2026-07-04 | Flyway `V1__init.sql`이 MySQL 8에서 구문 오류(ERROR 1064)로 실패 → 앱 부팅 불가(`flywayInitializer` 빈 생성 실패), 17개 중 12개 테이블만 부분 생성. 라이브 재현: `bootRun` + MySQL 8.0.46 컨테이너. | `rank`가 MySQL 8.0.2+ **예약어**인데 `V1__init.sql:192`(rank_entry)·`:231`(reward_item)에서 백틱 없이 컬럼명으로 사용. 설계문서 §2.12/§2.14가 예약어 주의 없이 명명. 웹 슬라이스 테스트는 DB 미접촉이라 구조적으로 검출 불가. | `backend/src/test/java/com/runningcrew/migration/R003FlywayMigrationLiveTest.java` — Testcontainers(MySQL 8) 실 DB에 V1 전체 적용 + 17테이블 전수 + `rank` 컬럼 존재 검증. **red→green 순서 준수**: 수정 전 SQL로 ERROR 1064 near 'rank' 실패(red) 확인 → 백틱 수정(`V1__init.sql` 2곳, 컬럼명 rank 유지) → 통과(green). `./gradlew build`에 편입(상시 실행). 라이브 완주 검증: compose MySQL + bootRun → health 200 + app-version 실응답 계약 일치 확인(03_backend_report.md §7). 설계문서 §2.12/§2.14에 예약어 각주 반영. 배치 B JPA 매핑 인용(`@Column(name="\`rank\`")` 또는 globally_quoted_identifiers)은 QA 3차 이월 항목 5로 추적. | CLOSED |
