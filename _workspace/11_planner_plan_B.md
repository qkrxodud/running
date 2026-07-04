# 11 — 기획자 작업 계획 (배치 B: 서버측 도메인 + 클라 소비 배선)

> 작성: planner · 2026-07-04 · 선행: `01_planner_plan.md`(배치 A, 보존), 커밋 779698c
> 기준: 계획서 §3/§5/§8, todolist(M1), `02_analyst_design.md`, `03/04_구현보고`, `05/06_테스트·QA보고`, `docs/regressions.md`
> 전제: M0 발급물 전부 미확보 유지 → 카카오/지도/Firebase/도메인 의존 작업은 계속 "대기".

---

## 1. 요청 요약 / 범위 판정

- **요청**: 배치 A 완료(백엔드 골조·클라 아키텍처·계약 v0.1, R-002/R-003 CLOSED)를 이어받아 배치 B(서버측 도메인 + 화면 뼈대)를 상세화.
- **범위 판정**: 전 항목 **MVP** — M1 소속(인증+User / Crew / Course+RaceSession 서버측, 화면 뼈대, CI). 범위 외·2차 침범 없음.
- **확정 결정 반영** (오케스트레이터 전달):
  - **O-1**: CrewMemberJoined·RewardGranted 알림은 **인앱 표시로 갈음**(FCM 2종 원칙 유지). ※ 오케스트레이터 경유 전달값 — 사용자 직접 확인이 아직 없으므로 §7에 확인 요청으로 유지하되, 계획은 이 값으로 진행.
  - **O-2**: go_router + Riverpod + dio — 배치 A에서 배선 완료, 종결.
  - 카카오 토큰 검증: **포트 + 스텁 어댑터** 방식 확정(배치 A 계획 §3의 판단대로).
- **작업량 판단**: User+인증, Crew, Race 서버측, 화면 뼈대 8종, CI를 한 배치에 담으면 과다 → **B1/B2 분할, B1만 상세화**.
  - **B1 (이번 상세화)** — *인증·User·Crew 수직 슬라이스 + 클라 HTTP 소비 개통 + 품질 가드 승격*: 서버 도메인 첫 진입이므로 헥사고날 실질 검증(ArchUnit)·ddl-auto validate·CI를 같이 세워, 이후 Race/Tracking이 검증된 레일 위에 올라가게 한다. 클라는 dio 실전송·DTO·인증 플로우를 개통해 **최초의 앱↔서버 3자 대조**를 성립시킨다.
  - **B2 (윤곽만)** — *Race 컨텍스트 서버측 + 세션 화면 뼈대 + 계약 확장(결과·히스토리)*: B1의 인증·크루 위에서만 성립(세션은 크루 소속).
- **분할 근거**: QA 이월 6건 중 5건이 B1 구획(인증·DTO·JPA·CI)에 자연 흡수된다(§5 흡수표). Race를 같이 넣으면 이월 해소 검증과 신규 도메인 리스크가 한 회차에 섞여 QA 판정이 흐려진다.

---

## 2. 배치 B1 — 상세 (형식: `[영역]` 작업명 — AC — 의존)

### 백엔드

- **B1-S1 [backend]** 인증 기반: `KakaoTokenVerifier` 포트 + 스텁 어댑터 + JWT 체계 + 인증 필터
  - AC: ① 포트 인터페이스는 user 컨텍스트 `application` 레이어 소유, 카카오 회원번호(KakaoAccount)는 인증 어댑터 밖 노출 0건(ArchUnit 규칙 — B1-S6과 연동). ② 스텁 어댑터는 **local/dev 프로필에만 활성**(프로필 미지정·prod에서 스텁 빈 로드 시 부팅 실패로 fail-fast). ③ `POST /api/v1/auth/login`(카카오 액세스 토큰 → JWT access/refresh 발급), `POST /api/v1/auth/refresh`, 만료 시 401 `{code,message}` — 계약(B1-S5) 준수. ④ 인증 필터: `/app-version`·`/actuator/health`·`/auth/*` 외 전부 인증 요구, 401 shape conventions §4 일치. ⑤ 실 카카오 어댑터는 **대기**(앱 키) — 포트 뒤 배선 지점만 주석 고정.
  - 의존: 없음 (B1 진입점) · domain-analyst 검토(토큰 수명·클레임)
- **B1-S2 [backend/domain]** User 애그리거트 + 온보딩 닉네임 + 탈퇴 처리(UserWithdrawn)
  - AC: ① User(닉네임·상태 ACTIVE/WITHDRAWN) 도메인 TDD — Spring/JPA 무관 순수 도메인(ArchUnit 검증 대상). ② 최초 로그인 시 User 생성 + 닉네임 설정 API. ③ 탈퇴 API: 닉네임 "탈퇴한 러너" 익명화, kakao_id·device_token 즉시 파기, track_payload 삭제 경로 구현(현재 데이터 없어도 이벤트 핸들러 존재), rank_entry·리플레이 파생물 보존 — UserWithdrawn 이벤트 발행·핸들러 유닛 테스트. ④ WITHDRAWN 사용자 토큰 무효화(재요청 401).
  - 의존: B1-S1 · **O-4(재가입 정책) 확정 전엔 "재로그인=신규 User" 기본값으로 구현**
- **B1-S3 [backend]** DeviceToken 등록/갱신 서버 API
  - AC: `PUT /api/v1/users/me/device-token`(fcm_token, platform) — upsert, 계약 준수. 클라 측 토큰 취득은 Firebase 필요 → **대기**(M3) 표기 유지.
  - 의존: B1-S1, B1-S2
- **B1-S4 [backend/domain]** Crew 컨텍스트: Crew·CrewMember·InviteCode + 불변식 + API
  - AC: ① 도메인 TDD: 크루장 항상 1명 / 크루장 탈퇴 시 **가입일 최선임 자동 승계** / 마지막 1인 탈퇴 시 CLOSED / 초대코드 만료·사용횟수 제한·코드로만 가입 — 각각 실패 케이스 포함 유닛 테스트. ② API: 크루 생성, 초대코드 생성, 코드로 참가, 내 크루 목록, 크루 상세(멤버 목록) — crew-api.md 준수(계약 v0.2 확장분 포함). ③ CrewMemberJoined 이벤트 발행 — **O-1 반영: FCM·별도 알림 저장 없음, 크루 상세 조회 표시로 갈음**(이벤트는 발행해 두어 확장 지점 보존). ④ UserWithdrawn 수신 → 멤버십 정리·승계 트리거 통합 테스트 1건.
  - 의존: B1-S1, B1-S2 · domain-analyst 검토(승계·CLOSED 전이표)
- **B1-S5 [backend/domain]** 계약 확장 v0.2: auth.md + user.md + crew-api.md 명령 상세
  - AC: `docs/contracts/`에 auth(로그인·갱신·401 규약), user(온보딩·프로필·탈퇴·디바이스토큰), crew 명령 상세(요청/응답 스키마·오류코드·**enum 값 집합 명시** — R-001 계약 템플릿 규칙). 서버 구현·앱 DTO가 이 문서 기준(3자 대조의 진실).
  - 의존: 없음 (B1-S1~S4와 병행 착수, 구현 전 확정) · domain-analyst 주도
- **B1-S6 [backend/test]** JPA·아키텍처 가드 승격 (QA 이월 3·4·5 흡수)
  - AC: ① Hibernate `globally_quoted_identifiers` 전역 채택(또는 전 엔티티 명시 인용) — R-003 파생 JPA `rank` 인용을 **선제 해소**(rank_entry 엔티티는 M2에 등장하지만 규약을 지금 고정). ② `ddl-auto: none → validate` 승격 — B1 엔티티(user/device_token/crew/crew_member/invite_code) 라이브 매핑 검증 후. ③ Instant 왕복(저장→조회 무손실) Testcontainers 테스트. ④ ArchUnit 도입: domain 패키지 spring/jakarta import 0건 + KakaoAccount 노출 금지 + 컨텍스트 간 직접 의존 금지(이벤트 경유만) — `./gradlew build` 편입.
  - 의존: B1-S1~S4 (엔티티 존재 후 validate 승격)

### 클라이언트

- **B1-C1 [flutter]** dio HTTP 어댑터 + DTO 규약 개통 (QA 이월 1·2·W-1 후속 흡수)
  - AC: ① `ApiClient`: base URL dart-define 주입(dev/prod 분리 골격), 오류 `{code,message}` 파싱, 401 인터셉터. ② **dio import는 어댑터 레이어에만** — `lib/core/` allowlist 가드(R-002 장치) green 유지. ③ **enum 미지값 폴백 유틸**(서버 enum → 클라 파싱, 미지값은 UNKNOWN 폴백 + 로깅) — R-001 유형 재발 방지, 계약 enum 값 집합과 테스트로 대조. ④ app-version DTO 구현 → **계약↔서버↔앱 3자 대조 최초 성립**(QA 3차 인계).
  - 의존: B1-S5 (계약 v0.2)
- **B1-C2 [flutter]** 인증 플로우: 토큰 저장 + dev 로그인 + 401 재로그인
  - AC: ① JWT 보안 저장(`flutter_secure_storage` 등), 자동 갱신, 401 → 재로그인 유도 플로우. ② **dev 전용 로그인 화면**(스텁 verifier 대응 자격 입력) — dev 플래그로 격리, prod 빌드에 미포함. ③ 카카오 로그인 화면·SDK는 **대기**(앱 키) — 로그인 화면 교체 지점만 주석 고정. ④ 로그아웃: 자체 토큰·(향후) 디바이스 토큰 정리.
  - 의존: B1-S1, B1-C1
- **B1-C3 [flutter]** 강제 업데이트 판단 로직 (`GET /app-version` 소비)
  - AC: 기동 시 min_version 비교(semver) → 미달 시 차단 다이얼로그. 판단 로직은 순수 Dart(버전 비교 유닛 테스트), 네트워크 실패 시 통과(가용성 우선 — 홈서버 다운이 앱 사용을 막지 않게).
  - 의존: B1-C1
- **B1-C4 [flutter]** 온보딩 닉네임 + 설정/프로필 + **앱 내 회원 탈퇴 UI**
  - AC: 디자인 기준(1a 라임 토큰) 준수. 최초 로그인 → 닉네임 설정 → 홈. 설정: 닉네임 수정·로그아웃·탈퇴(2단 확인, UserWithdrawn 진입점 — Play 계정 삭제 요건). 방침·약관 링크는 **URL 미정(도메인 대기)** — placeholder 상수로 격리.
  - 의존: B1-S2, B1-C2
- **B1-C5 [flutter]** 홈 + 크루 목록·상세 + 크루 생성 + 초대코드 입력 참가 플로우
  - AC: 홈(내 크루·예정 레이스 자리) / 크루 목록·상세(멤버 목록 — **합류 표시는 여기서 갈음**, O-1) / 크루 생성 / 코드 입력 → 참가. 초대코드 "생성·공유"는 코드 문자열 표시+클립보드까지(카톡 공유 링크·딥링크는 **대기** — 도메인·카카오). 위젯 테스트 최소 1종/화면.
  - 의존: B1-S4, B1-C1, B1-C2
- **B1-T1 [test]** CI 구성 (GitHub Actions) + Docker 이미지 기동 검증 (QA 이월 1차분 흡수)
  - AC: ① PR/푸시 시 `flutter analyze`+`flutter test`, `./gradlew build`(Testcontainers 동작 — R-003 마이그레이션 테스트 상시 실행) 자동. ② **앱 Docker 이미지 빌드 + compose 기동 + health 200 잡** 포함 — QA 2차 미검증 잔여(이미지 자체 기동) 해소. ③ 실패 시 머지 차단.
  - 의존: 없음 (즉시 착수 가능 — B1 첫 주 병행 권장)

**B1 규모**: backend 6 + flutter 5 + test 1 = **12개 항목**. 발급물 의존 0(대기 지점은 항목 내 명시).
**병렬성**: B1-S5(계약)·B1-T1(CI) 선행/병행 → S1→S2→{S3,S4}→S6, C1→{C2,C3}→{C4,C5}. 서버·클라는 계약 v0.2 확정 후 병렬.

---

## 3. 배치 B2 — 윤곽 (B1 완료 후 상세화)

- **[backend/domain]** Course 애그리거트: RoutePath VO, 발행 후 불변, 폴리라인 서버 측 디코딩/거리 계산 — **폴리라인 상호운용**(QA 이월 6: 1e-5 정밀도 대조 + test-engineer 특이사항 tie 반올림 half-away-from-zero 채택, 클라 골든 벡터 `_p~iF~ps|U…`로 서버 테스트 박제).
- **[backend/domain]** RaceSession + Participation: 상태머신(DRAFT→…→COMPLETED/CANCELLED) TDD, upload_deadline NOT NULL(+12h는 앱 UX 기본값), 취소 정책(RUNNING 중 취소 가능·트랙 개인 기록 보존), STARTED 신호 API(멱등).
- **[backend]** 계약 v0.3: session-api 명령 상세 + 조회 확장(세션 목록/상세·참가자 상태 — 결과/순위·히스토리·PB 계약은 M2 구현 전 초안만).
- **[flutter]** 세션 상세 화면("지금 뛰는 중" 표시 포함) + 세션 생성 UI — 코스 선택은 **시드/더미 코스**로(지도 그리기 UI는 네이버 Client ID 대기).
- **[flutter]** dev/prod 환경 분리 마무리(flavor/dart-define 구조 — 키 값 주입 지점만, 실제 키는 대기).
- **[test]** Crew·Race 도메인 골든/상태머신 테스트 확장, QA 4차(세션 계약 3자 대조).

M2로 넘기는 것(배치 B 아님): 트래킹 실배선(P-2 트래커 1시간 실기기, P-4 notification 대칭 호출), TrackRecord·업로드 파이프라인, FinishPolicy·RankingPolicy.

---

## 4. 대기 목록 (변동 없음 — M0 발급물 게이트)

| 작업 | 필요 발급물 | 배치 B 내 대체/격리 |
|---|---|---|
| 카카오 로그인 실연동·화면 | 카카오 앱 키 | B1-S1 스텁 어댑터 + B1-C2 dev 로그인 |
| 초대 카톡 공유·딥링크·랜딩 | 도메인(+카카오) | B1-C5 코드 표시+클립보드까지 |
| 지도·코스 그리기 UI | 네이버 Client ID | B2 시드 코스 |
| FCM 클라 토큰 취득·Crashlytics | Firebase | B1-S3 서버 API만 |
| 방침·약관 URL | 도메인(Pages) | B1-C4 placeholder 상수 |

---

## 5. QA·테스트 이월 항목 흡수표

| 이월 항목 (출처) | 흡수 작업 | 처리 |
|---|---|---|
| 앱 Docker 이미지 자체 기동 미검증 (06 §4, 03 §6.3) | **B1-T1** | CI에 이미지 빌드+기동+health 잡 |
| app-version 3자 대조 (06 이월 1·2) | **B1-C1** | 앱 DTO 구현 → QA 3차에서 대조 수행 |
| 헥사고날 실질 검증 + ArchUnit (06 이월 3) | **B1-S6** | ArchUnit 3규칙 build 편입 |
| ddl-auto validate 승격 + Instant 왕복 (06 이월 4, 03 보류-1) | **B1-S6** | B1 엔티티 검증 후 승격 |
| JPA `rank` 인용 (06 이월 5, R-003 파생) | **B1-S6** | globally_quoted_identifiers 전역 채택으로 선제 해소(엔티티는 M2) |
| enum 미지값 폴백 (06 이월 2, R-001 유형) | **B1-C1** | 폴백 유틸 + 계약 enum 집합 대조 테스트 |
| dio 배선 후 core 순수성 (06 W-1 후속, R-002) | **B1-C1** | allowlist 가드 상존 — QA 3차 재확인 항목으로 명시 |
| 폴리라인 정밀도·tie 반올림 (06 이월 6, 05 §4) | **B2 Course** | 서버 최초 폴리라인 접점에서 상호운용 테스트 박제 |
| P-2 트래커 1시간 실기기 / P-4 notification 대칭 (06 1차) | **M2 이월 유지** | 트래킹 실배선 시점 소관 — 배치 B 범위 아님 |
| 구조화 로그 JSON 인코더 (03 보류-2) | **M0/M4 이월 유지** | 인프라 항목 |

---

## 6. 마일스톤 매핑

- B1·B2 전 항목 → **M1** (인증+User / Crew / Race 서버측·화면 뼈대·CI). B1-S6·B1-T1은 "상시 점검" 체계의 M1 시점 구축분.
- 스파이크 실기기 검증(1시간·실주행)은 여전히 **사용자 대기** — 배치 B와 독립 진행 가능하나, **M2 착수 전 판정 필수**(fail fast 게이트) 재강조.

---

## 7. 열린 질문

- **O-1 (재확인 요청)**: "인앱 표시 갈음" 결정은 오케스트레이터 경유 전달 — 사용자 직접 확인을 다음 접점에서 1회 요청. 계획은 이 값으로 진행하며, 뒤집힐 경우 B1-S4의 이벤트 발행 구조 덕에 소비자만 추가하면 됨(구조 변경 없음). 아울러 "인앱 표시"의 최소 해석(별도 알림함 없음, 크루 상세 멤버 목록으로 갈음)을 채택했음 — 알림함 UI를 원하면 회신 요망.
- **O-4 (신규 — 계획서 미기재)**: **탈퇴 후 동일 카카오 계정 재로그인 정책.** 탈퇴 시 kakao_id를 파기하므로 재로그인은 자연히 **신규 User 생성**(과거 기록·PB와 완전 분리)이 기본값. 이 기본값으로 B1-S2를 구현하되, "복구 유예 기간" 등 다른 의도가 있으면 회신 요망.
- **O-5 (신규 — 낮음)**: JWT 수명·갱신 정책(access/refresh 만료값) — 계약 v0.2에서 domain-analyst 제안값으로 확정 예정, 사용자 결정 불요(이의 시만).
