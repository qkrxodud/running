# 15 — 테스트 보고 (배치 B1: 백엔드 순수 함수 골든/유닛 테스트)

> 작성: test-engineer · 2026-07-04 · 입력: `13_backend_report_B1.md §3`(이관 함수), 계약 `crew-api.md v0.2`·`user-api.md v0.1`, 스킬(golden-testing, domain-model)
> 방법론: 기대값은 계약·설계에서 도출(구현 실행 결과 복사 금지). 상태전이(승계 TX·CLOSED·재가입 복원)는 backend-dev 통합 테스트가 이미 커버 → 순수 함수 단위 경계만 신규 작성.

## 0. 결과 요약

- **신규 테스트 34개, 전부 통과.** `./gradlew test --rerun-tasks` 전체 스위트 **48 tests / 0 failures / 0 errors**(Testcontainers MySQL 통합 포함, Docker UP).
- 발견 버그: **없음**. 4개 대상 함수 모두 계약·설계 스펙과 일치.
- 순수성 반려: **없음**. 4개 함수 전부 IO·시계·랜덤 없는 순수 함수 — 즉시 골든화 가능.

## 1. 커버 함수 · 케이스

### 1.1 `InviteCode` — 12 케이스 (`crew/domain/InviteCodeTest.java`)
근거: crew-api §5(만료·소진 오류), domain-model Crew 불변식(`used_count <= max_uses`, 만료 UTC 판정).

- **isExpired(now)** 6: 직전 1초(false) / 경계 동일시각(**true**, 경계는 만료) / 직후 1ms(true) / 나노초 경계(−1ns false, +1ns true) / Instant.MIN(false) / Instant.MAX(true).
- **isExhausted()** 6: 5중4(false) / 5중5 경계(true) / 5중6 초과(true) / 1회용 미사용(false)·1회 후(true) / incrementUse 후 소진 / **max_uses=0**(계약상 1~100 범위 밖=어댑터 400 차단, 순수 함수는 "0회 허용=항상 소진" 논리 귀결로 true — 주석에 계약 근거 명시).

### 1.2 `LeaderSuccessionPolicy.selectSuccessor` — 7 케이스 (`crew/domain/LeaderSuccessionPolicyTest.java`)
근거: crew-api §Enum(가입일 최선임 승계, 동률 id 오름차순), domain-model(크루장 항상 1명).

- 가입일 최선임 선정 / 가입일 동률→id 오름차순(tie-break) / WITHDRAWN은 가입일 이르러도 제외 / 단일 ACTIVE 후보 / 빈 리스트→empty / 전원 WITHDRAWN→empty / 가입일 우선 후 id(큰 id라도 이른 가입일 승리).

### 1.3 `Nickname.normalize` — 15 케이스 (`user/domain/NicknameTest.java`)
근거: user-api §2(trim 후 1~30자, 제어문자 금지, 유일성 없음).

- **정상 5**: 앞뒤 공백 트리밍 / 내부 공백 보존 / 하한 1자 / 상한 30자 / 유일성 없음(동일 문자열 통과).
- **길이 위반 5**: null / 빈 문자열 / 공백만(trim 후 0) / 31자 초과 / 공백 덧댄 32자→trim 후 30자 허용(길이는 trim 기준).
- **제어문자 5**: 내부 탭 / 내부 개행 / 널문자(U+0000) → 각 예외; 앞뒤 탭·개행은 trim 제거되어 통과 / 일반 기호·숫자 통과.

## 2. 발견 버그

없음. 구현 4종 모두 스펙 일치 확인:
- `isExpired`: `!now.isBefore(expiresAt)` = now≥expiresAt → 경계 포함 만료. 계약 일치.
- `isExhausted`: `usedCount >= maxUses`. 계약 일치.
- `selectSuccessor`: ACTIVE 필터 + joinedAt→id(null은 MAX_VALUE로 후순위) 정렬. 계약 일치.
- `normalize`: trim→길이(1~30)→제어문자 순 검증. 계약 일치.

## 3. 특이사항 · 판정 근거

- **max_uses=0 판정**: 계약 crew-api §4가 `max_uses 1~100`으로 규정 → 0은 무효 입력이며 어댑터(400 VALIDATION_ERROR)에서 차단된다. 순수 함수 `isExhausted`는 입력 검증을 하지 않으므로, max=0을 만나면 `0>=0`=소진으로 귀결된다. 이는 구현 역산이 아니라 "0회 허용=항상 소진"이라는 의미 도출이며, 테스트 주석에 계약 근거를 남겨 "0 미허용은 도메인이 아니라 경계에서 막힌다"를 문서화했다.
- **`incrementUse()`는 상태 변경 함수**라 골든 대상 아님(부작용 있음). 단 `incrementUse→isExhausted` 연쇄를 1케이스로 확인해 1회용 코드의 소진 전이를 박제.
- **재가입 서열 회귀**(joined_at이 재참가 시각으로 갱신되어 승계 서열이 뒤로 감)는 `CrewMember.restore()`의 상태 변경 + Crew.join TX 경로 → backend-dev 통합 테스트(`CrewWithdrawalSuccessionIntegrationTest`) 소관. 여기선 selectSuccessor가 "주어진 joined_at"에 대해 최선임을 고르는 순수 규칙만 검증(중복 회피).
- **픽스처 없음**: 4함수 전부 손계산 가능한 스칼라·소규모 리스트 입력이라 `fixtures/` 불필요. 실주행 트랙 픽스처는 B2(Tracking/Ranking/Replay) 대상.
- 테스트 이름은 규칙을 한글 문장(`@DisplayName`)으로 서술 — 실패 메시지만으로 깨진 도메인 규칙 식별 가능.

## 4. 팀 통신

- **backend-dev**: 이관 4함수 전부 스펙 일치, 반려·버그 없음. 순수성 양호.
- **오케스트레이터**: B1 백엔드 순수 함수 골든 게이트 통과(34/34, 전체 48/48 green). B2에서 FinishPolicy·TrackRefinement·RankingPolicy·추월 계산 골든 + 실주행 픽스처 축적 예정.
