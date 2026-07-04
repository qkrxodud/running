# 12 — 도메인 설계·계약 확정안 (배치 B1: 인증·User·Crew 수직 슬라이스)

> 작성: domain-analyst · 2026-07-04 · 기준: `11_planner_plan_B.md`(B1), `02_analyst_design.md`(보존 — 스키마·규약 유효), `06_qa_report.md`(이월), `domain-model` 스킬, 계획서 §5
> 선행 설계(02)와 충돌 없음 — 본 문서는 B1 델타만 다룬다. session-api·Race 설계는 B2(손대지 않음).

---

## 0. 결정 반영 현황

| 항목 | 값 | 출처/비고 |
|---|---|---|
| O-1 | 합류·지급 알림 **인앱 갈음, 최소 해석**(별도 알림함 없음 — 크루 상세 멤버 목록 표시로 충분) | 오케스트레이터 경유 전달(사용자 확정 표기). planner §7의 재확인 요청은 유지 — 뒤집혀도 이벤트 발행 구조라 소비자 추가만으로 대응(§4) |
| O-4 | 탈퇴 후 동일 카카오 재로그인 = **신규 User 생성, 과거 기록 완전 분리** | 오케스트레이터 경유 기본값 확정. kakao_id 즉시 파기 구조의 논리적 귀결 — 서버는 재로그인을 신규 가입과 구분할 수단 자체가 없다(의도된 설계) |
| M-4 (←02 §5) | 익명화 닉네임 = **고정 문자열 `탈퇴한 러너`** | 본 문서에서 확정(§1.3). 파생 행은 user_id로 구분 유지되므로 표시 중복은 무해 |
| M-6/O-5 (←02 §5) | JWT 스펙 | 본 문서에서 확정(§2.3) |

---

## 1. User 애그리거트 (B1-S2)

### 1.1 구성

- **User** (애그리거트 루트, `user/domain`): `id`, `nickname`, `status(ACTIVE/WITHDRAWN)`, `createdAt`, `withdrawnAt`, `onboardedAt`. 순수 도메인 — Spring/JPA 금지(ArchUnit §5).
- **KakaoAccount** (VO, `user/domain`): 카카오 회원번호 봉인. **user 컨텍스트 밖 노출 금지 + user의 web 어댑터에서도 참조 금지**(ArchUnit R-3). 영속은 `user.kakao_id` 컬럼.
- 도메인 이벤트: `UserWithdrawn(userId)`.

### 1.2 온보딩 (스키마 델타 V2 — 확정)

`user.nickname`은 V1에서 NN인데 최초 로그인 시점엔 닉네임이 없다. 해결:

- **V2 마이그레이션**: `ALTER TABLE \`user\` ADD COLUMN onboarded_at TIMESTAMP(6) NULL` (UTC, `_at` 규약).
- 최초 로그인 시 User 생성: `nickname` = 서버 생성 placeholder(구현 자유, 예: `러너`+랜덤 4자리 — 계약은 non-null만 보장), `onboarded_at = NULL`.
- `PUT /users/me/nickname` 성공 시 `onboarded_at`이 NULL이면 now(UTC)로 기록. 계약의 `onboarding_completed = (onboarded_at != null)`.
- 근거: "is_new 플래그를 로그인 응답에만 실으면 온보딩 중단 후 재로그인 시 상태를 잃는다" — 서버 컬럼이 진실이어야 클라 재설치에도 온보딩 게이트가 복원된다.

닉네임 검증(확정): trim 후 **1~30자**(V1 VARCHAR(30) 정합), 빈 문자열·제어문자 금지. **유일성 없음**(미규정 → 확정: 중복 허용 — 스키마에 UQ 없고 크루 내 표시명 성격. 식별은 user_id).

### 1.3 탈퇴 처리 — UserWithdrawn (익명화 순서, 단일 트랜잭션)

```
DELETE /users/me (인증 사용자 본인만)
 TX 시작
 1. User 로드(FOR UPDATE) — status ACTIVE 확인, 아니면 409
 2. status → WITHDRAWN, withdrawn_at = now(UTC)
 3. nickname → "탈퇴한 러너" (고정 문자열, M-4 확정), kakao_id → NULL (파기)
 4. device_token: 해당 user_id 행 전부 DELETE (식별 정보)
 5. track_payload: 해당 유저의 track_record_id 전부에 대해 DELETE (위치 원본 파기)
    — B1 시점 데이터 없어도 핸들러 경로는 존재해야 함 (planner AC)
 6. UserWithdrawn(userId) 발행 → 동기 @EventListener(같은 TX):
    Crew 컨텍스트가 멤버십 정리·승계 수행 (§3.3)
 TX 커밋
```

- **보존(건드리지 않음)**: `track_record`(요약 메타), `rank_entry`, `race_result`, `participation`, `replay_snapshot` 행 — user_id 유지, 표시명은 조회 시 user.nickname 조인으로 자연 익명화(3에서 이미 덮어씀).
- **M2 의무 기록**: ReplaySnapshot `payload`가 닉네임 사본을 내장하는 설계라면 탈퇴 핸들러가 기존 READY 스냅샷 payload 내 닉네임도 재작성해야 한다. **제안(M2에서 확정)**: payload에는 user_id만 넣고 표시명은 조회 시 해석 — 그러면 재작성 불요. replay 설계 시 이 항목을 반드시 재점검.
- **토큰 무효화**: 별도 블랙리스트 없음 — 인증 필터가 **매 요청 user status 조회**(§2.3), WITHDRAWN이면 401. MVP 규모(홈서버, 소수 크루)에서 per-request 조회 비용은 무시 가능하고, 리프레시 무효화도 같은 경로로 해결된다.
- **O-4 귀결**: 이후 동일 카카오 계정 로그인 → kakao_id 매칭 실패 → 신규 User 생성. 과거 기록·PB와 완전 분리. 복구 유예 없음(확정 기본값).

### 1.4 User 불변식 체크리스트

| # | 불변식 | 강제 수단 |
|---|---|---|
| U-B1 | WITHDRAWN 사용자는 어떤 인증 API도 통과 불가 (401) | 코드 — 인증 필터 status 체크 (테스트 필수) |
| U-B2 | 탈퇴 시 kakao_id·device_token·track_payload 동일 TX 내 파기 | 코드 — 탈퇴 유스케이스 (핸들러 유닛 테스트) |
| U-B3 | 탈퇴 시 rank_entry·track_record·participation 행 보존 | 코드 + 스키마(FK RESTRICT가 방어선 — 02 §4.2 U-2) |
| U-B4 | nickname 항상 non-null·1~30자 | 스키마 NN + 코드 검증 |
| U-B5 | 탈퇴는 본인만, ACTIVE에서만 (중복 탈퇴 409) | 코드 |
| U-B6 | KakaoAccount는 user 컨텍스트 밖·web 어댑터에 노출 0건 | ArchUnit R-3 |

---

## 2. 인증 경계 (B1-S1)

### 2.1 KakaoTokenVerifier 포트

- 위치: `user/application/port/out/KakaoTokenVerifier` (application 레이어 소유 — 헥사고날 out-port).
- 시그니처: `KakaoAccount verify(String kakaoAccessToken) throws KakaoTokenInvalidException`.
- 소비자: 로그인 유스케이스만. 반환된 KakaoAccount는 user 조회/생성에 쓰고 **응답·타 컨텍스트로 나가지 않는다**.

### 2.2 스텁 어댑터 (M0 카카오 키 대기)

- `StubKakaoTokenVerifier` — `@Profile({"local","dev"})` 한정. **prod/프로필 미지정에서는 이 빈이 없어 포트 주입 실패 → 부팅 실패(fail-fast)** = 스텁이 운영에 새는 사고를 구조로 차단.
- 스텁 규약(계약 auth-api.md에 명시): 토큰 형식 `stub:{fake_kakao_id}`만 수용 → `KakaoAccount(fake_kakao_id)` 반환. 그 외 전부 `AUTH_KAKAO_TOKEN_INVALID`. 같은 fake_kakao_id 재로그인 = 같은 User(실카카오와 동일 의미론 — dev에서 O-4 시나리오도 재현 가능).
- 실 어댑터 배선 지점: `user/adapter/out/kakao/` — 주석으로 고정, M0 키 확보 후 `@Profile("prod")` 어댑터만 추가.

### 2.3 JWT 스펙 (M-6/O-5 — 확정)

| 항목 | 값 | 근거 |
|---|---|---|
| 서명 | **HS256**, 시크릿 env `JWT_SECRET`(≥256bit), yml 하드코딩 금지 | 단일 서버 모놀리스 — 비대칭 불필요 |
| access 수명 | **30분** | 필터가 매 요청 status 조회하므로 장수명도 안전하나, 탈취 노출 최소화 표준값 |
| refresh 수명 | **30일** | 주 1~2회 러닝 사용 패턴에서 재로그인 강요 없는 최소값 |
| 클레임 | `sub`(내부 user id, 문자열) · `typ`("access"\|"refresh") · `iat` · `exp` · `jti`(refresh만, UUID) | **kakao_id·닉네임 등 개인정보 클레임 금지**(봉인 원칙) |
| 갱신 | `POST /auth/refresh` — refresh 검증 후 **access+refresh 쌍 재발급(회전)** | 구식 refresh의 서버측 폐기는 없음(무상태) — 잔여 위험은 §8 잔여-1 |
| 무효화 | 서버측 저장소 없음. WITHDRAWN 차단은 필터의 per-request status 조회로 달성 | MVP 단순성. 강제 로그아웃/디바이스 관리 필요 시 2차에 jti 저장 도입 |
| 401 구분 | `AUTH_TOKEN_EXPIRED`(만료 — 갱신 시도 대상) vs `UNAUTHORIZED`(부재·위조·WITHDRAWN — 재로그인) vs `AUTH_REFRESH_INVALID`(갱신 실패 — 재로그인) | 클라 분기가 code로만 가능해야 함(conventions §4) |
| 필터 제외 | `/api/v1/app-version`, `/api/v1/auth/**`, `/actuator/health` | planner AC ④ |

클라 플로우(계약 명시): 보호 API 401 `AUTH_TOKEN_EXPIRED` → refresh 1회 → 원요청 재시도. refresh도 401 → 토큰 폐기·재로그인. 그 외 401 → 즉시 재로그인.

---

## 3. Crew 애그리거트 (B1-S4)

### 3.1 구성

- **Crew** (루트): `id`, `name`, `leaderId`, `status(ACTIVE/CLOSED)`, `createdAt` + 멤버십 컬렉션(CrewMember: `userId`, `role(LEADER/MEMBER)`, `joinedAt`, `status(ACTIVE/WITHDRAWN)`).
- **InviteCode** (별도 소엔티티/애그리거트): `code`(자연키), `crewId`, `expiresAt`, `maxUses`, `usedCount`.
- 이벤트: `CrewMemberJoined(crewId, userId)` — `crew/domain/event/`.

### 3.2 유스케이스 규칙

- **생성**: 생성자 = leader_id + role LEADER 멤버십 동시 생성(같은 TX). CLOSED 크루에 어떤 명령도 불가(409 `CREW_CLOSED`).
- **초대코드 생성**(크루장 전용): `expires_at = now(UTC) + expires_in_hours`. 코드 형식(확정): **대문자+숫자 6자, 혼동 문자(0/O/1/I) 제외**, 충돌 시 재생성. `expires_in_hours` 기본 72h는 **클라/앱레이어 UX 기본값** — 도메인 하드코딩 금지(02 규약 동일 패턴).
- **코드 참가**: 단일 TX에서 ①코드 존재 ②`expires_at > now(UTC)` ③`used_count < max_uses` ④크루 ACTIVE ⑤비멤버(WITHDRAWN 재가입은 **허용 — 새 멤버십 행이 아니라 기존 행 ACTIVE 복원 + joined_at 갱신**, UQ(crew_id,user_id) 제약과 정합) 검증 → 멤버십 확정 + `used_count += 1` + `CrewMemberJoined` 발행. 동시 참가 경합은 InviteCode 행 잠금(FOR UPDATE)으로 직렬화.

### 3.3 크루장 승계·CLOSED (UserWithdrawn 소비 — 컨텍스트 간 이벤트 경유)

crew 컨텍스트의 `@EventListener`(동기, 같은 TX — 탈퇴와 승계가 원자적이어야 "크루장 없는 크루" 관측 불가):

```
UserWithdrawn(userId) 수신 → 해당 유저의 ACTIVE 멤버십 각각에 대해:
 1. 멤버십 status → WITHDRAWN
 2. 탈퇴자가 leader인가?
    a. 남은 ACTIVE 멤버 있음 → joined_at 최소(동률 시 id 오름차순 — tie-break 확정)
       멤버를 role LEADER 승격 + crew.leader_id 갱신
    b. 남은 ACTIVE 멤버 없음 → crew.status = CLOSED (leader_id는 마지막 값 유지 — 이력)
 3. 탈퇴자가 leader 아님 → 멤버십 정리만
```

- **조회 모델 주의**: 멤버 목록의 닉네임은 crew 컨텍스트가 user 도메인 클래스를 참조하지 않고 **persistence 어댑터의 SQL 조인(DTO 직반환)**으로 해석한다 — 클래스 의존이 없으므로 ArchUnit R-2 위반 아님. 명령 경로는 이벤트로만(규범 유지).

### 3.4 Crew 불변식 체크리스트

| # | 불변식 | 강제 수단 |
|---|---|---|
| C-B1 | ACTIVE 크루엔 role=LEADER인 ACTIVE 멤버 정확히 1명 + crew.leader_id와 일치 | 코드 — 승계 TX (실패 케이스 포함 TDD, planner AC ①) |
| C-B2 | 크루장 탈퇴 → joined_at 최선임 자동 승계 (동률 tie-break: id 오름차순) | 코드 |
| C-B3 | 마지막 1인 탈퇴 → CLOSED, CLOSED엔 참가·코드생성·세션생성 전부 409 | 코드 |
| C-B4 | 가입은 초대코드로만 (공개 경로 없음), used_count ≤ max_uses·만료 UTC 판정 | 코드 + InviteCode 행 잠금 |
| C-B5 | 중복 가입 불가 (재가입은 기존 행 복원) | 스키마 UQ(crew_id,user_id) + 코드 |
| C-B6 | 탈퇴·승계는 UserWithdrawn 이벤트 경유만 (user→crew 직접 호출 금지) | ArchUnit R-2 |

---

## 4. 인앱 알림 — O-1 최소 해석의 이벤트 소비 설계

- **결정**: FCM 2종 원칙(리마인더·리플레이) 유지. CrewMemberJoined·RewardGranted는 푸시·알림함·전용 테이블·전용 API **전부 없음**.
- **표시 갈음**: 합류 = 크루 상세 `members`(joined_at 포함 — 최근 합류가 자연 노출). 지급 = M3 보상 장부 화면 소관(B1 무관).
- **이벤트는 발행한다**: `CrewMemberJoined`는 B1에서 소비자 없이 발행만(로그 소비자 1개 허용). 이유: O-1이 뒤집힐 경우(알림함/FCM 확장) **소비자만 추가**하면 되는 확장 지점 보존 — 발행을 생략하면 결정 번복 시 유스케이스를 다시 연다.
- 금지: 이 결정을 근거로 FCM 인프라·notification 테이블을 선제 도입하는 것(범위 가드).

---

## 5. 품질 가드 (B1-S6) — ArchUnit 규칙 + JPA 규약

`./gradlew build`에 편입되는 ArchUnit 테스트 4규칙:

| # | 규칙 | 구현 힌트 |
|---|---|---|
| R-1 | `..domain..` 클래스는 `org.springframework..`·`jakarta..`·`org.hibernate..` 의존 금지 | `noClasses().that().resideInAPackage("..domain..")...` |
| R-2 | 컨텍스트 간 클래스 의존 금지 — 예외는 `common..`과 **타 컨텍스트의 `..domain.event..`만** (이벤트 클래스는 소비측이 import 가능) | 컨텍스트 6종 각각 slice 규칙. 이벤트는 반드시 `{ctx}/domain/event/` 패키지에 둘 것(규칙의 전제) |
| R-3 | `KakaoAccount`는 `com.runningcrew.user..` 밖에서 의존 금지 **+ `user.adapter.in.web`에서도 금지** (application·out-adapter만) | 클래스 단위 규칙 |
| R-4 | 레이어 방향: `domain`은 `application`/`adapter` 의존 금지, `application`은 `adapter` 의존 금지 | onion 규칙 |

JPA·스키마 규약(ArchUnit 아님, 같은 작업으로 고정):

- **`hibernate.globally_quoted_identifiers=true` 전역 채택** — R-003(`rank` MySQL 예약어) 파생의 선제 해소. rank_entry 엔티티는 M2 등장이지만 규약은 지금 고정, B1 엔티티 5종(user/device_token/crew/crew_member/invite_code)으로 라이브 검증. 주의: 전역 인용 시 V1 DDL의 식별자와 대소문자 일치 확인(Testcontainers validate가 잡는다).
- `ddl-auto: none → validate` 승격 — B1 엔티티 매핑 완료 후.
- Instant 왕복(저장→조회 무손실) Testcontainers 테스트 — QA 이월 4 해소.
- **V2 마이그레이션**(§1.2 onboarded_at)도 Testcontainers 마이그레이션 테스트 대상에 포함.

---

## 6. 클라이언트 enum 미지값 폴백 규약 (B1-C1, R-001 유형 방지)

1. 서버 enum을 파싱하는 **모든** DTO는 공용 유틸 경유: `parseEnum<T>(raw, {계약 값 집합}, fallback: T.unknown)` — Dart enum마다 `unknown` 멤버 추가.
2. 미지값 수신 시: 크래시 금지, `unknown` 폴백 + 로깅(현재 debug log, Crashlytics 배선 후 승격). UI는 unknown을 안전 표기(예: 상태 미상 배지)로 처리.
3. **계약 대조 테스트 의무**: DTO enum의 문자열 집합 == 계약 문서의 값 집합임을 검증하는 유닛 테스트를 enum마다 둔다 (`user.status`, `crew.status`, `crew_member.role`, `platform` — B1 범위). 값 집합의 진실은 `docs/contracts/`.
4. **송신은 폴백 금지**: 클라가 서버로 보내는 enum(platform 등)에 unknown 직렬화 금지 — 파싱 전용 안전장치다.

---

## 7. 계약 산출 (B1-S5)

- `docs/contracts/auth-api.md` **신규 v0.1** — 로그인(카카오→JWT)·갱신·401 규약·스텁 모드.
- `docs/contracts/user-api.md` **신규 v0.1** — me 조회·닉네임 설정/수정·탈퇴·디바이스 토큰 등록.
- `docs/contracts/crew-api.md` **v0.1 → v0.2 승격** — 명령 상세 확정(enum·오류코드 전수 명시), 변경 이력 주석.
- `docs/contracts/conventions.md` **v0.1 → v0.1.1** — 오류 code 집합에 AUTH_* 3종 추가(변경 이력 주석).
- session-api.md **무변경** (B2).

---

## 8. 미규정 잔여

- **잔여-1 (2차)**: refresh 회전 시 구토큰 서버측 폐기 없음(무상태) — 탈취 재사용 위험은 수명 30일로 한정. 강제 로그아웃/기기 관리 필요 시 jti 저장 도입(2차, 선구현 금지).
- **잔여-2 (M2 재점검)**: ReplaySnapshot payload의 표시명 내장 여부 — §1.3 제안(user_id만 내장, 표시명 조회 시 해석)을 replay 설계 시 확정.
- **잔여-3 (후속 배치)**: "크루 나가기"(탈퇴 아닌 크루 탈퇴) 유스케이스 — B1 범위 아님(계획서 명시 흐름은 회원 탈퇴 경유만). 필요 시 planner 판단으로 배치 편성, 승계 로직은 §3.3 재사용 가능.
- **잔여-4 (O-1 재확인)**: 인앱 갈음은 오케스트레이터 경유 값 — 사용자 직접 확인 1회 요청 유지(planner §7과 동일 입장).
