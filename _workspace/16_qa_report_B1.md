# 16 — QA 검증 보고 (배치 B1: 인증·User·Crew 앱↔서버 3자 대조)

> 작성: qa · 2026-07-04 · 대상: `backend/`(B1-S1~S6) + `app/`(B1-C1~C5)
> 기준: `docs/contracts/`(auth v0.1·user v0.1·crew v0.2·conventions v0.1.1·app-version v0.1), `13_backend_report_B1.md`, `14_flutter_report_B1.md`, 스킬(qa-integration·domain-model)
> 처음으로 **계약·서버·클라 3자가 모두 존재** — 실행 대조(라이브 curl 왕복)까지 수행.

## 판정 요약

**PASS — 차단 0건 / 경고 0건 / 참고 3건.** 이전 회차 이월 항목 전량 종결 또는 정당한 이월.

| 검증 범위 | 결과 | 방식 |
|---|---|---|
| 1. 3자 대조 (auth·user·crew·app-version 필드 단위) | 통과 (불일치 0) | 정적 + **라이브 curl 왕복** |
| 2. enum 값 집합 5종 (R-001 유형) + unknown 폴백 | 통과 | 정적 + 실행(테스트) + 라이브 실측 |
| 3. 401 분기 정합 (AUTH_* 3종 ↔ 인터셉터) | 통과 | 정적 + 라이브 실측 |
| 4. 탈퇴 상호작용 (6단계 TX·익명/파기 구분·204→401·O-4) | 통과 | 정적 + **라이브 실측** |
| 5. 실행 검증 (gradle test·flutter test·compose·bootRun·curl) | 통과 | 실행 |
| 6. 이월 재검증 (R-002 core 순수성·R-003 ddl validate) | CLOSED 재확인 | 실행 |

---

## 이전 발견 재검증 (회귀 확인 — 신규 범위에 앞서)

- **R-002 (core 순수성, dio 배선 후)** — CLOSED 재확인. `grep` 전수: `lib/core` 전체에 `package:` / `dart:ui|isolate|ffi` import **0건**(dio·secure_storage·riverpod 전부 `lib/data`·`lib/app` 격리). allowlist 가드 테스트 `test/core/no_platform_imports_test.dart` 포함 flutter test 92/92 green. 배치 B의 dio 실배선 후에도 core 유출 없음 — R-002 종결 유효.
- **R-003 (`rank` 예약어 / ddl-auto validate)** — CLOSED 재확인. `application.yml:17 ddl-auto: validate` + `:24 globally_quoted_identifiers: true`. bootRun이 validate 통과로 부팅 성공(health 200) = 엔티티 5종이 마이그레이션된 스키마(`rank` 백틱 컬럼 포함)와 정합. Testcontainers 마이그레이션 라이브 테스트(`R003FlywayMigrationLiveTest`) `./gradlew test` BUILD SUCCESSFUL. 이월 항목 5(JPA `rank` 인용)는 `globally_quoted_identifiers`로 해소 — 별도 `@Column` 백틱 불필요.

---

## 1. 3자 대조 — 계약 ↔ 서버 record ↔ 클라 DTO (불일치 0건)

방식: 정적 필드 대조 + **라이브 서버(8081, MySQL 컨테이너) 실응답 캡처** → 클라 `fromJson` 키와 대조. 라이브 응답 원문은 §5에 발췌.

### auth-api §1 login (`LoginResponse` ↔ `LoginResponse.java` ↔ `auth_dtos.dart`)
| 계약 필드 | 서버 emit(라이브) | 클라 parse | 판정 |
|---|---|---|---|
| access_token / refresh_token | `access_token`/`refresh_token` | `json['access_token']`/`['refresh_token']` | 일치 |
| token_type "Bearer" | `"Bearer"` | `json['token_type']` | 일치 |
| expires_in int(1800) | `1800` | `json['expires_in'] as int` | 일치 |
| is_new_user bool | `true` | `json['is_new_user']` | 일치 |
| user{id,nickname,onboarding_completed} | `{"id":1,"nickname":"러너5937","onboarding_completed":false}` | `AuthUser.fromJson` 동일 키 | 일치 (user 객체에 status·kakao 미노출 — 계약대로) |

### auth-api §2 refresh (`TokenResponse.java` ↔ `RefreshResponse`)
`is_new_user`·`user` 제외 shape — 서버 record가 정확히 4필드. 클라 `RefreshResponse.fromJson` 일치.

### user-api §1 me (`UserResponse.java` ↔ `UserProfile`)
| 계약 | 서버(라이브) | 클라 | 판정 |
|---|---|---|---|
| id,nickname,status(enum),onboarding_completed,created_at | `{"id":1,"nickname":"민수","status":"ACTIVE","onboarding_completed":true,"created_at":"2026-07-04T07:19:42.836245Z"}` | `UserProfile.fromJson`(status=`UserStatus.parse`) | 일치. created_at UTC Z + 마이크로초 → Dart `DateTime.parse` 수용 확인 |

### crew-api §3 CrewDetail (`CrewDetailResponse.java` ↔ `CrewDetail`)
라이브: `leader{user_id,nickname}`, `members[{user_id,nickname,role,joined_at}]`, `status`, `created_at` 전부 snake_case. 클라 `CrewMemberView.fromJson`이 `user_id`·`role`·`joined_at` 동일 키 파싱. **members ACTIVE만·joined_at 오름차순** 라이브 확인(참가 후 2명, LEADER→MEMBER 순). 일치.

### crew-api §2 목록 + 페이지 래퍼 (`PageResponse` ↔ `page_response.dart`)
라이브: `items[{id,name,status,member_count,role,created_at}]`, `page,size,total_elements,total_pages`. 클라 `PageResponse.fromJson` + `CrewSummary.fromJson` 동일 키. 일치.

### crew-api §4 invite-code (`InviteCodeResponse.java` ↔ `InviteCodeInfo`)
라이브: `{"code":"PWTS5L","crew_id":1,"expires_at":"…Z","max_uses":5,"used_count":0}`. 코드 6자·혼동문자(0/O/1/I) 제외 실측(`PWTS5L`). 클라 `InviteCodeInfo.fromJson` 일치. 클라 `InviteCodeFormat.allowedChars`(`ABCDEFGHJKLMNPQRSTUVWXYZ23456789`) = 서버 코드 문자 집합과 정합.

### app-version §1 (`AppVersionResponse` ↔ `AppVersionInfo`)
클라 `AppVersionInfo.fromJson`: `platform`(enum parse)/`min_version`/`updated_at`. 서버 record(배치 A 검증)와 일치. 클라 송신 `platform.toWire()`="ANDROID" ↔ 서버 `@RequestParam Platform`. **배치 A→B1 이월(앱 DTO 후 3자 완성) 종결.** (이번 세션 app-version 라이브 미재현 — 배치 A에서 라이브 확인·B1은 DTO 정적 대조. 참고 P16-2.)

---

## 2. enum 값 집합 대조 (R-001 유형) — 통과

서버 enum(`@Enumerated(STRING)` + Jackson 기본 `name()` 직렬화) ↔ 클라 `enum_codec` wire 집합 ↔ 계약, 3자 전수 일치. Jackson `property-naming-strategy: SNAKE_CASE`는 필드명만 — enum **값**은 `name()`(대문자) 그대로 직렬화됨을 라이브 실측으로 확인(`"ACTIVE"`,`"LEADER"`,`"MEMBER"`).

| enum | 계약 | 서버 domain enum | 클라 wireValues | 라이브 실측 |
|---|---|---|---|---|
| user.status | {ACTIVE,WITHDRAWN} | `UserStatus{ACTIVE,WITHDRAWN}` | 동일 | `"ACTIVE"` |
| crew.status | {ACTIVE,CLOSED} | `CrewStatus{ACTIVE,CLOSED}` | 동일 | `"ACTIVE"` |
| crew_member.role | {LEADER,MEMBER} | `CrewRole{LEADER,MEMBER}` | 동일 | `"LEADER"`,`"MEMBER"` |
| crew_member.status | {ACTIVE,WITHDRAWN} | `CrewMemberStatus{ACTIVE,WITHDRAWN}` | 동일 | (응답 미노출¹) |
| platform | {ANDROID,IOS} | `Platform{ANDROID,IOS}` | 동일 | (요청 파라미터) |

- unknown 폴백: `enum_contract_test.dart`가 값 집합 대조(5종) + 미지값→unknown + null→unknown + 관측 훅 로깅 + 송신 폴백 금지(`ContractEnumSendError`)를 박제. **R-001 재발 방지 장치로 유효** — flutter-dev 권고대로 이 테스트를 R-001 상시 장치로 지정 타당(§참고 P16-1).
- ¹ `crew_member.status`는 CrewDetail이 ACTIVE 멤버만 반환하므로 응답에 안 실림 — 클라 `CrewMemberStatus`는 현재 파싱 미사용(dead지만 계약 집합 보존 목적, 무해).

---

## 3. 401 분기 정합 — 통과 (정적 + 라이브)

서버 `JwtAuthenticationFilter` 발행 조건 ↔ 클라 `AuthInterceptor` 분기 대조:

| 조건 | 서버 발행 code | 라이브 실측 | 클라 인터셉터 행동 |
|---|---|---|---|
| access 만료 | `AUTH_TOKEN_EXPIRED` | (만료 유도 불가·정적) | refresh 1회→재시도 (`auth_retried` 플래그) |
| 헤더 부재 | `UNAUTHORIZED` | `no-token → UNAUTHORIZED` ✓ | 즉시 `_expireSession` |
| 위조 | `UNAUTHORIZED`(TokenInvalidException) | (정적) | 즉시 `_expireSession` |
| WITHDRAWN | `UNAUTHORIZED`(status 조회) | `탈퇴 후 me → UNAUTHORIZED` ✓ | 즉시 `_expireSession` |
| refresh 엔드포인트 실패 | `AUTH_REFRESH_INVALID` | (정적) | `auth_repository.refresh`가 null→재로그인 |

- 클라는 **code로만 분기**(conventions §4 준수, message 매칭 없음) — `AUTH_TOKEN_EXPIRED`만 갱신, 그 외 재로그인. refresh 요청은 `isNoAuthPath('/api/v1/auth/')` true라 인터셉터가 우회 → **무한 갱신 루프 구조적 차단**. `auth_interceptor_test.dart` 5케이스(만료→재시도 성공 / UNAUTHORIZED 즉시만료 / refresh 성공했으나 재시도도 401→refresh 여전히 1회 / refresh null→즉시만료 / auth 경로 401 무간섭)로 박제, flutter test green.

---

## 4. 탈퇴 상호작용 — 통과 (정적 + 라이브 실측)

서버 `UserAccountService.withdraw` + `User.withdraw(now)` 불변식 코드 확인:
- **파기**: `nickname="탈퇴한 러너"`(익명화 고정문자열), `kakaoAccount=null`(kakao_id 즉시 파기), `deviceTokenRepository.deleteByUserId`(디바이스 토큰), track_payload는 `UserWithdrawn` 동기 소비자(tracking)가 같은 TX에서 파기.
- **익명 보존**: rank_entry·리플레이 파생물 — B1엔 Race 미구현이라 대상 없음(FK RESTRICT 방어선은 R-003 재검증에서 확인). 파기/보존 뒤바뀜 **없음** — domain-model User 불변식 정합.
- **단일 TX**: `@Transactional` 하 상태전이→저장→토큰파기→이벤트발행. ACTIVE 아니면 no-op(멱등).
- **라이브 실측**: `DELETE /users/me → 204` → 동일 토큰 재요청 `401 UNAUTHORIZED` → **동일 카카오 재로그인 = 신규 User**(`is_new_user:true`, user_id 2→3). 계약 "재로그인=신규계정"(O-4) + 토큰 즉시 무효 확정.
- **클라 상호작용**: `auth_controller.withdraw` = `_users.withdraw()`(204) → `_auth.logout()`(로컬 토큰 폐기) → `loggedOut` 전이. 서버 토큰 무효(이후 UNAUTHORIZED)와 정합 — 이미 로컬 폐기했으므로 후속 401 미발생 경로. 정합.

---

## 5. 실행 검증 (조용한 생략 없음)

| 단계 | 수행 | 결과 |
|---|---|---|
| `./gradlew test` | 실행 | **BUILD SUCCESSFUL** (ArchUnit·R-003 마이그레이션 라이브·app-version 슬라이스·탈퇴/승계 통합·**AuthCrewHttpFlow(MockMvc+Testcontainers MySQL) 왕복**·Instant 왕복) |
| `flutter test` | 실행 | **92/92 passed**(enum 계약 대조·auth 인터셉터 5케이스·upload C-5·core 순수성 가드 포함) |
| `docker compose up -d mysql` | 실행 | healthy |
| `bootRun`(8081, local) | 실행 | health 200(8080은 우리 앱 아닌 java 프로세스 점유 — 배치 A/B1 보고와 동일) |
| **라이브 curl 왕복** | 실행 | login→onboarding→me→crew 생성→invite→목록→멤버 참가(**소문자 코드 정규화 확인**: `pwts5l`→가입)→탈퇴→재로그인 신규 + 오류 4종(UNAUTHORIZED/AUTH_KAKAO_TOKEN_INVALID/INVITE_CODE_INVALID 404/ALREADY_JOINED 409) **전부 계약 문자 단위 일치** |
| `docker compose down -v` | 실행 | volume+network 원복 완료 |

라이브 응답 실측 발췌(모두 snake_case·UTC Z·enum 대문자):
```
login  : {"access_token":"…","token_type":"Bearer","expires_in":1800,"is_new_user":true,"user":{"id":1,"nickname":"러너5937","onboarding_completed":false}}
me     : {"id":1,"nickname":"민수","status":"ACTIVE","onboarding_completed":true,"created_at":"2026-07-04T07:19:42.836245Z"}
crew   : {"id":1,"name":"새벽 러닝크루","status":"ACTIVE","leader":{"user_id":1,"nickname":"민수"},"created_at":"…Z","members":[{"user_id":1,"nickname":"민수","role":"LEADER","joined_at":"…Z"}]}
invite : {"code":"PWTS5L","crew_id":1,"expires_at":"2026-07-07T07:19:43.163581Z","max_uses":5,"used_count":0}
list   : {"items":[{"id":1,"name":"…","status":"ACTIVE","member_count":1,"role":"LEADER","created_at":"…Z"}],"page":0,"size":20,"total_elements":1,"total_pages":1}
errors : UNAUTHORIZED / AUTH_KAKAO_TOKEN_INVALID / INVITE_CODE_INVALID(404) / ALREADY_JOINED(409)
```
**INVITE_CODE_INVALID 404 교정**: 서버 `ErrorCode.INVITE_CODE_INVALID=NOT_FOUND(404)` 라이브 확인. 클라는 code 문자열로만 분기(HTTP status 무관)이므로 교정이 클라에 투명하게 반영 — `crew_join_screen._messageFor`가 `INVITE_CODE_INVALID` 케이스 보유. 정합.

---

## 참고 (P — 차단/경고 아님, 후속 관측)

- **P16-1 (3자 파싱 상호검증의 CI 부재)**: 서버는 jsonPath로 계약을, 클라는 `enum_contract_test`로 계약을 **각각 독립 앵커**한다(qa-integration 원칙대로 계약이 기준). 하지만 "서버 실 바이트 ↔ 클라 DTO 파싱" 교차는 이번처럼 QA 라이브 런에서만 닫힌다 — CI엔 없음. MVP엔 적정. 후속(B2+) 권고: 서버가 생성한 응답 픽스처를 앱 테스트가 파싱하는 공유 픽스처 테스트. flutter-dev 권고대로 `enum_contract_test`는 **R-001 상시 재발 방지 장치로 지정** 타당(레지스트리 R-001 장치 열에 이미 유사 항목 기재).
- **P16-2 (app-version 라이브 미재현)**: 이번 세션 라이브 왕복은 auth/user/crew만. app-version은 DTO 정적 대조 + 배치 A 라이브 근거로 통과. app_min_version 시드 유무에 따른 200/404 클라 처리(`fetch`가 404→null→게이트 통과)는 정적 확인. B2 라이브 스모크에 포함 권고.
- **P16-3 (compose 앱 컨테이너 자체 기동 이월 유지)**: 라이브는 `bootRun`(로컬 JVM→컨테이너 MySQL) — 앱 Docker 이미지 자체 기동(JDK25 베이스 pull)은 배치 A·B1 동일하게 미실행. backend 보류-2 / CI(B1-T1)에서 해소 예정. **비차단 이월.**

---

## 6. 이월 항목 처리 상태

| 이월(출처) | 상태 |
|---|---|
| 앱↔서버 3자 대조 (2차 이월 2, 1차 이월 1) | **종결** — auth/user/crew/app-version 전량 대조 완료 |
| enum 미지값 폴백 (1차 이월 2) | **종결** — 5종 계약 대조 + unknown 폴백 테스트 확인 |
| R-002 core 순수성 dio 배선 후 (1차 이월 3) | **CLOSED 재확인** |
| R-003 ddl-auto validate·`rank` JPA 인용 (2차 이월 4·5) | **CLOSED 재확인** — globally_quoted_identifiers로 해소 |
| 헥사고날 불변식 실질 검증 (2차 이월 3) | **부분 종결** — ArchUnit 4규칙 도입·green(backend §0). 도메인 클래스 생성 후 실질 검증됨 |
| compose 앱 컨테이너 자체 기동 (2차 보류) | **이월 유지**(P16-3, CI B1-T1) |
| 폴리라인 정밀도 1e5/1e6 (1차 이월 6) | **B2 이월** — 트래킹 업로드 API 미구현 |
| track_payload 격리·순위·상태머신(RaceStatus) | **B2 이월** — Race 컨텍스트 미구현. 단 track_payload 접근이 탈퇴 파기 경로 한정임은 확인(순위/크루 조회에 payload 연관 0건) |
| P-4 notification start/stop 대칭·P-2 실기기 1시간 유실 | **이월 유지** — 트래킹 UI 실배선(M1+) 시점 |

## 7. 팀 통신

- **backend-dev / flutter-dev**: 차단·경고 0건 — B1 경계면 무결. `INVITE_CODE_INVALID` 404 교정·소문자 코드 정규화·탈퇴 O-4 라이브 확인.
- **domain-analyst**: 계약 결함 없음. crew-api v0.2 오류코드 8종·enum 3집합 서버/클라 정합.
- **test-engineer**: `enum_contract_test`(앱)를 R-001 상시 장치로 지정 권고(P16-1). backend §3 순수함수(InviteCode·LeaderSuccessionPolicy) 골든화는 별도 트랙.
- **오케스트레이터**: 신규 회귀 등록 없음(R-002·R-003 CLOSED 유지). 이월은 전부 B2/CI 범위의 정당한 이월.
