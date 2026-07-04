# 13 — 백엔드 구현 보고 (배치 B1: 인증·User·Crew 수직 슬라이스)

> 작성: backend-dev · 2026-07-04 · 기준: `11_planner_plan_B.md`(B1-S1~S6), `12_analyst_design_B.md`, 계약 `docs/contracts/`(auth·user·crew·conventions), 스킬(backend-hexagonal, domain-model)
> 선행: `03_backend_report.md`(배치 A). 이전 세션 토큰 리밋 중단 → 인프라 골조(build.gradle·compose·yml·V2) 유지·보완 후 Java 소스 전량 신규 구현.

---

## 0. 빌드·라이브 결과 (완료 기준)

- **`./gradlew build` → BUILD SUCCESSFUL.** 테스트 15개 전부 통과(0 실패). Testcontainers(MySQL 8) 통합 테스트 포함.
  - ArchUnit 4규칙, R-003 마이그레이션 라이브, app-version 슬라이스, 탈퇴·승계 통합(2), 인증·크루 HTTP 왕복(2), Instant 왕복 라이브.
- **라이브 곡선 완주**: `docker compose up -d mysql`(healthy) → `bootRun`(local 프로필, health 200 UP) → 스텁 로그인 → JWT로 크루 생성→초대 코드 발급→(소문자 코드) 참가 왕복 → **응답이 계약과 문자 단위 일치 확인** → `docker compose down -v`(볼륨 원복).
  - 확인된 계약 정합: snake_case 전 필드, UTC `Z` ISO-8601(예 `2026-07-04T07:12:16.322294Z`), 초대 코드 6자 혼동문자 제외(`VVQQP6`), 코드 대문자 정규화, 401 code 세분(`UNAUTHORIZED`/`AUTH_KAKAO_TOKEN_INVALID`), 페이지 래퍼(`total_elements` 등).
- **환경 주의**: 이 머신 8080 포트를 정체불명 java 프로세스가 점유 중이어서(우리 앱 아님) 라이브 검증은 8081로 수행. compose 앱 컨테이너 자체 기동은 배치 A와 동일하게 미실행(JDK 25 베이스 이미지 pull 비용) — QA 이월 유지, CI(B1-T1)에서 해소 예정.

### 이전 세션 산출물 인벤토리 결과 (재작성 없이 유지/보완)
| 대상 | 상태 | 조치 |
|---|---|---|
| `build.gradle.kts` | JWT·ArchUnit·Testcontainers·validation 의존 이미 포함 | ArchUnit `1.4.0→1.4.1` 승격(1.4.0 ASM이 Java 25 바이트코드 미파싱 → 규칙이 클래스 0개 검사) |
| `application.yml` | `ddl-auto: validate`·`globally_quoted_identifiers: true`·jwt·local 프로필 완비 | 유지 |
| `docker-compose.yml` | mysql·app·UTC·healthcheck 완비 | 유지 |
| `V2__add_user_onboarded_at.sql` | onboarded_at NULL 컬럼 | 유지(라이브 validate 통과) |

---

## 1. 구현 내역 (계획 항목 대비)

| 항목 | 상태 | 비고 |
|---|---|---|
| B1-S1 인증 기반(포트+스텁+JWT+필터) | 완료 | `KakaoTokenVerifier` 포트(application) + `StubKakaoTokenVerifier`@Profile{local,dev} + prod fail-fast, `JwtTokenProvider`(HS256, 30분/30일, 쌍 회전), `JwtAuthenticationFilter`(화이트리스트 + per-request status 조회) |
| B1-S2 User 애그리거트·온보딩·탈퇴 | 완료 | 순수 `User`(닉네임·상태·onboarded_at), 탈퇴 6단계 단일 TX + `UserWithdrawn` 동기 소비(crew·tracking) |
| B1-S3 DeviceToken 등록 API | 완료 | fcm_token 기준 upsert |
| B1-S4 Crew·CrewMember·InviteCode·불변식·API | 완료 | 승계·CLOSED·재가입 복원·초대코드 만료/횟수, crew-api v0.2 전체 |
| B1-S6 품질 가드 | 완료 | ArchUnit 4규칙, ddl-auto validate 라이브 검증, Instant 왕복, `globally_quoted_identifiers` |
| KakaoAccount 봉인(R-3) | 완료 | user 밖·user.adapter.in.web 참조 0건(ArchUnit 강제). 응답·클레임에 kakao_id 미노출 |

### 헥사고날 배치 (신규)
```
user/  domain(User·KakaoAccount·Nickname·UserStatus·event/UserWithdrawn)
       application(AuthService·UserAccountService·LoginResult, port/out: KakaoTokenVerifier·TokenProvider·UserRepository·DeviceTokenRepository + 토큰 예외)
       adapter/in/web(AuthController·UserController·JwtAuthenticationFilter·AuthFilterConfig + dto)
       adapter/out/kakao(StubKakaoTokenVerifier)  out/token(JwtTokenProvider)  out/persistence(User·DeviceToken 엔티티·어댑터)
crew/  domain(Crew·CrewMember·InviteCode·LeaderSuccessionPolicy·enums·event/CrewMemberJoined)
       application(CrewCommandService·CrewQueryService·CrewMembershipCleanupService·InviteCodeGenerator·CrewMemberJoinedLogListener·view + ports)
       adapter/in/web(CrewController + dto)  adapter/out/persistence(Crew·CrewMember·InviteCode 엔티티·어댑터·CrewQueryAdapter[native user 조인])
tracking/ application(TrackDataCleanupListener + port/out TrackDataEraser)  adapter/out/persistence(TrackPayloadEraseAdapter)
common/ web(AuthUserId·ArgumentResolver·AuthAttributes·WebMvcConfig), error(ErrorCode 확장), TimeConfig(Clock 빈)
```

---

## 2. 계약 대비 구현 API (11개)

| 계약 | 엔드포인트 | 인증 | 구현·검증 |
|---|---|---|---|
| auth §1 | `POST /api/v1/auth/login` | 불요 | 스텁→User 확정→JWT. is_new_user·user{onboarding_completed}. 실패 401 AUTH_KAKAO_TOKEN_INVALID |
| auth §2 | `POST /api/v1/auth/refresh` | 불요 | refresh 검증(WITHDRAWN 거부)→쌍 회전. 실패 401 AUTH_REFRESH_INVALID |
| user §1 | `GET /api/v1/users/me` | 필요 | 프로필(snake_case, created_at UTC) |
| user §2 | `PUT /api/v1/users/me/nickname` | 필요 | 온보딩 겸용(최초 성공 시 onboarded_at 기록) |
| user §3 | `DELETE /api/v1/users/me` | 필요 | 204. 탈퇴 6단계 단일 TX, 멱등 |
| user §4 | `PUT /api/v1/users/me/device-token` | 필요 | 204. fcm_token upsert |
| crew §1 | `POST /api/v1/crews` | 필요 | 201 CrewDetail(생성자=LEADER) |
| crew §2 | `GET /api/v1/crews` | 필요 | 페이지 래퍼, member_count·role |
| crew §3 | `GET /api/v1/crews/{crewId}` | 필요 | CrewDetail(멤버 전용 403, 없음 404) |
| crew §4 | `POST /api/v1/crews/{crewId}/invite-codes` | 필요 | 201. 크루장 전용(403), CLOSED 409 |
| crew §5 | `POST /api/v1/crews/join` | 필요 | 200 CrewDetail. 검증순서 코드→만료→소진→ACTIVE→멤버십, 코드행 FOR UPDATE |

- 오류코드 정합: `INVITE_CODE_INVALID`를 **404**로 교정(배치 A ErrorCode가 409였음 — crew-api §5 준수). `AUTH_KAKAO_TOKEN_INVALID/AUTH_TOKEN_EXPIRED/AUTH_REFRESH_INVALID` 추가. 요청 본문 미지 enum·형식 오류는 `HttpMessageNotReadableException` → 400 VALIDATION_ERROR로 통일.

---

## 3. test-engineer 이관: 순수 도메인 함수 (골든/유닛 테스트 대상)

프레임워크·IO·시계·랜덤 없음. 시그니처 + 예시 입출력 합의 넘김.

### 3.1 `com.runningcrew.crew.domain.InviteCode`
- `boolean isExpired(Instant now)` — 참가 유효 조건 `expires_at > now`의 부정. **경계값(now == expires_at)은 만료(true)**.
  - expiresAt=`2026-07-07T00:00Z`: now=`…06T23:59Z`→**false**, now=`…07T00:00:00Z`→**true**, now=`…07T00:00:01Z`→**true**
- `boolean isExhausted()` — `used_count >= max_uses`.
  - maxUses=5,usedCount=4→**false** / usedCount=5→**true** / usedCount=6→**true**
- `void incrementUse()` — usedCount+1(참가 성공 시). 경계 케이스: exhausted 확인은 호출측 책임.

### 3.2 `com.runningcrew.crew.domain.LeaderSuccessionPolicy`
- `static Optional<CrewMember> selectSuccessor(List<CrewMember> candidates)` — ACTIVE만 후보, **joined_at 최소**(최선임), 동률 시 **멤버십 id 오름차순**(tie-break 확정). 후보 없으면 `empty`(→ 호출측 CLOSED).
  - `[{id2,joined=T2,ACTIVE},{id3,joined=T1,ACTIVE}]` → **id3**(T1<T2)
  - `[{id5,joined=T,ACTIVE},{id4,joined=T,ACTIVE}]` → **id4**(joined 동률 → id 오름차순)
  - `[{id2,joined=T,ACTIVE},{id1,joined=T,WITHDRAWN}]` → **id2**(WITHDRAWN 후보 제외)
  - `[]` 또는 전부 WITHDRAWN → **Optional.empty**
- 주의: 재가입 멤버는 joined_at이 재참가 시각으로 갱신되므로 서열이 뒤로 감(설계 §3.2 — 복원 의미론).

### 3.3 (부가 후보) `com.runningcrew.user.domain.Nickname.normalize(String) : String`
- trim 후 1~30자·제어문자 금지, 유일성 없음. 위반 시 `InvalidNicknameException`. 계획 필수 이관 대상은 아니나 순수 함수라 골든화 가능.

> 애그리거트 상태전이(Crew.join 재가입 복원, handleMemberWithdrawn 승계/CLOSED)는 통합 테스트(`CrewWithdrawalSuccessionIntegrationTest`)로 backend-dev가 이미 커버. test-engineer는 위 순수 함수의 경계 카탈로그 확장 담당.

---

## 4. QA 검증 경계면 (점진 QA 트리거)

1. **계약↔서버 3자 대조(auth·user·crew)**: 라이브 왕복 응답이 계약 예시와 일치함을 §0에서 확인. flutter-dev DTO(B1-C1)와의 3자 대조는 QA 3차 소관 — enum 집합(`user.status`, `crew.status`, `crew_member.role`, `platform`) 문자열이 계약과 동일.
2. **인증 필터 경계**: 화이트리스트(`/app-version`,`/auth/**`,`/actuator`) 외 401. 만료→`AUTH_TOKEN_EXPIRED`, 부재·위조·WITHDRAWN→`UNAUTHORIZED`(per-request status 조회로 탈퇴 즉시 무효). 필터 실패 응답도 `{code,message}` shape(필터가 직접 직렬화).
3. **탈퇴 원자성**: 식별정보(닉네임 익명화·kakao_id·device_token·track_payload) 파기 + 크루 승계/CLOSED가 **단일 TX**. 통합 테스트로 승계 체인·CLOSED·디바이스토큰 파기 검증. **O-4**(동일 카카오 재로그인=신규 User) 확인.
4. **ddl-auto validate 라이브**: B1 엔티티 5종(user/device_token/crew/crew_member/invite_code) + V2 매핑이 실 MySQL에서 validate 통과(부팅 성공이 증거). Instant TIMESTAMP(6) 마이크로초 왕복 무손실.
5. **track_record/track_payload 분리**: payload 접근은 tracking 어댑터 native delete(탈퇴 경로) 한정. 순위·크루 조회 경로에 payload 블롭 로드 0건(엔티티 연관 자체 없음).
6. **동시성**: 참가는 invite_code 행 FOR UPDATE로 직렬화, UQ(crew_id,user_id) 정합. 탈퇴는 user 행 FOR UPDATE.

---

## 5. 미완료·보류

- **[보류-1] 실 카카오 어댑터**: M0 앱 키 대기. `user/adapter/out/kakao/`에 `@Profile("prod")` 어댑터 추가 지점만 주석 고정. 스텁은 local/dev 한정, prod 미주입 시 부팅 실패(구조적 fail-fast).
- **[보류-2] 앱 Docker 이미지 자체 기동**: bootRun으로 대체 검증. CI(B1-T1)에서 이미지 빌드+compose 기동+health 잡으로 해소 예정(QA 이월).
- **[보류-3] refresh 무상태 회전의 구토큰 폐기 없음**(설계 §8 잔여-1) — 수명 30일로 한정, 2차 jti 저장. 선구현 금지.
- **[보류-4] "크루 나가기"(탈퇴 아닌 크루 탈퇴)**: B1 범위 아님(설계 §8 잔여-3). `Crew.join`의 WITHDRAWN 재가입 복원 경로는 구현돼 있어(도메인·영속) 후속 배치에서 재사용 가능. 현재 crew_member가 WITHDRAWN 되는 유일 경로는 회원 탈퇴(=신규 user).
- **[결정 의존] O-1(인앱 갈음)**: `CrewMemberJoined`는 로그 소비자만(FCM·알림함 없음). 번복 시 소비자 추가만으로 대응.
- **[미착수/범위 밖]** Race/Course/세션(B2), FCM 실발송, 구조화 JSON 로그(M0).

## 6. 팀 통신

- **domain-analyst**: 계약 모호점 없이 구현. 단 배치 A `ErrorCode.INVITE_CODE_INVALID`가 409였던 것을 crew-api §5대로 **404로 교정**함(계약이 진실). 이견 시 통지 바람.
- **test-engineer**: §3 순수 함수 2종(+1 부가) 이관 — 시그니처·경계 예시 포함. 픽스처 없이 즉시 골든화 가능.
- **flutter-dev**: auth/user/crew 11개 엔드포인트 계약대로 구현·라이브 확인 완료(소비 가능). enum 집합은 계약과 동일 — B1-C1 폴백 유틸 대조 테스트 기준으로 사용.
- **qa**: §4 경계면 6종. 3자 대조(계약↔서버 라이브 확인됨, 서버↔클라는 B1-C1 이후). CI 이미지 기동 잔여 이월.
