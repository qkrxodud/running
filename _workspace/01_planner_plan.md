# 01 — 기획자 작업 계획 (M1 "발급물 없이 가능한 것" 배치)

> 작성: planner · 기준 문서: `app/docs/러닝크루_앱_계획서.md`, `app/docs/todolist.md`
> 컨텍스트: M1 1주차 트래킹 스파이크 **코드 완료**(`app/lib/spike/`, `app/lib/core/tracking/`), 실기기 검증만 사용자 대기.
> M0 발급물 미확보(카카오 앱 키 / 네이버 지도 Client ID / Firebase·google-services.json / 도메인) → **의존 작업 전면 제외·"대기" 표기**.

---

## 1. 요청 요약 / 범위 판정

- **요청**: todolist의 M1 항목 중 지금(발급물 없이) 구현 가능한 것을 골라 이번 배치의 작업 계획을 세운다.
- **범위 판정**: 전 항목 **MVP(1차 목표)** — 계획서 §3 MVP·§8 M1에 정면으로 대응. 범위 외(안티치트·공개 모집·참가비) 없음. 2차(M5) 침범 없음.
- **작업량 판단**: M1의 "발급물 무관" 표면이 넓어 **1회 배치로는 과다** → **2개 배치로 분할**하고 아래처럼 처리한다.
  - **배치 A (이번 상세화)** — *골조 + 계약*: 백엔드 프로젝트 부트스트랩·데이터 모델·시간대 규약·API 계약 초안 + 클라이언트 아키텍처 격리 3인터페이스·순수 Dart 코어·스캐폴드 정리. 발급물 완전 무관, 모든 후속 작업의 언블로커.
  - **배치 B (윤곽만)** — *서버측 도메인 구현 + 화면 뼈대 UI*: User/Crew/Course/RaceSession 애그리거트(서버), JWT 토큰 체계(카카오 검증 스텁), 화면 뼈대. 배치 A 산출물(패키지 구조·마이그레이션·계약·라우팅) 위에서 진행.
- **리스크 우선순위 근거**(계획서 §8): 최상위 리스크인 트래킹 스파이크는 이미 코드화·실기기 검증 대기 상태이므로, 이번 배치는 **다음 순위 불확실성 — "Android 전용 코드의 코어 침투"(§9 리스크 표)와 "경계면 계약 부재"** 를 먼저 못박는 데 집중한다. 격리 3인터페이스·순수 Dart 코어·API 계약을 배치 A에서 확정해 이후 도메인/화면 작업이 잘못된 토대 위에 쌓이지 않게 한다.

---

## 2. 배치 A — 골조 + 계약 (이번 상세화 대상)

형식: `[영역] 작업명 — 수용 기준(AC) — 의존`

### 백엔드 (`running/backend`, 현재 비어 있음)

- **A-B1 [backend]** Spring Boot 3.5+/Java 25 프로젝트 생성 + 헥사고날 패키지 골격
  - AC: `./gradlew build` 성공. 컨텍스트별 패키지 7종(`user`/`crew`/`race`/`tracking`/`ranking`/`reward`/`replay`) × 헥사고날 레이어(`domain`/`application`/`adapter.in.web`/`adapter.out.persistence`) 골격 존재. 스프링 부팅 시 `/actuator/health` 200.
  - 의존: 없음 (이 배치의 진입점)
- **A-B2 [backend]** Docker Compose (Spring Boot + MySQL 8) + restart 정책
  - AC: `docker compose up`으로 앱 컨테이너가 MySQL에 접속해 부팅, 헬스 green. 컨테이너 `restart: unless-stopped`(정전 복구 무인 재기동 전제, 계획서 §6).
  - 의존: A-B1
- **A-B3 [backend]** Flyway 도입 + V1 초기 마이그레이션 (계획서 §7 전 테이블)
  - AC: 부팅 시 마이그레이션 자동 적용·성공. §7 15개 테이블 전부 + **누락 주의 컬럼** 포함: `device_token.platform`, `user.withdrawn_at`, `reward_grant.sent_at`, `race_session.replay_notified_at`, `replay_snapshot.schema_version`/`status`, `track_payload` 1:1 분리 테이블, `app_min_version`. UTC 저장 전제로 시각 컬럼 타입 통일.
  - 의존: A-B1, A-B2 · **domain-analyst 검토 필요**(컬럼·제약 대조)
- **A-B4 [backend/domain]** 시간대 규약 확정 + 공통 직렬화 설정
  - AC: "서버 저장 UTC / 표시 KST" 규약을 계약 문서에 명문화. JPA·Jackson이 시각을 UTC `Instant`로 저장/직렬화(오프셋 명시)하도록 설정. `scheduled_at`·`upload_deadline`·리마인더 발송·마감 판정이 전부 UTC 기준임을 코드 주석·문서로 고정.
  - 의존: A-B1
- **A-B5 [backend/test]** REST API 계약 문서 초안 (`docs/contracts/`)
  - AC: `docs/contracts/`에 계약 파일 생성(경로·메서드·요청/응답 스키마·에러 규약·인증 요구). **조회 세트**: 내 크루 목록 / 크루 상세 / 세션 목록·상세(참가자 상태) / 결과·순위 / 개인 히스토리·PB. **명령 세트(계약만, 구현은 배치 B/이후)**: 로그인·토큰갱신, 크루 생성·참가(초대코드), 세션 생성, '레이스 시작' STARTED 신호, 트랙 업로드, DeviceToken 등록. 401→재로그인 규약 포함.
  - 의존: A-B4 · **domain-analyst 검토 필요**(도메인 상태·불변식 반영)
- **A-B6 [backend]** `GET /app-version` + `app_min_version` 최소 골격
  - AC: 플랫폼별 최소 지원 버전 반환하는 읽기 전용 엔드포인트. 마이그레이션(A-B3)에 테이블 포함. 클라이언트 강제 업데이트 판단 로직의 서버 측 전제 완성(클라 판단 로직은 배치 B/화면 뼈대에서).
  - 의존: A-B1, A-B3

### 클라이언트 (`running/app`)

- **A-C1 [flutter]** `LocationTracker` 인터페이스 정의 (격리 지점 ①)
  - AC: 순수 Dart 추상 인터페이스 — `start/stop/pause`, `Stream<TrackPoint>` 포인트 스트림, 상태 enum(READY/RUNNING/PAUSED/STOPPED 등). 기존 스파이크(`lib/spike/tracking_task_handler.dart`)의 실제 시그니처를 참조해 확정. 구현체 없이 컴파일·`flutter analyze` green.
  - 의존: 없음
- **A-C2 [flutter]** `NotificationService` 인터페이스 정의 (격리 지점 ②)
  - AC: 포그라운드 서비스 상시 알림(Android) vs 로컬 알림(iOS 확장)을 같은 추상으로 표현하는 시그니처. 순수 Dart, 구현체 없음.
  - 의존: 없음
- **A-C3 [flutter]** `PermissionService` 인터페이스 정의 (격리 지점 ③)
  - AC: 위치·알림 권한 요청/상태 조회 플로우 추상화(OS별 분기를 구현체로 미루는 시그니처). 순수 Dart.
  - 의존: 없음
- **A-C4 [flutter/test]** 플랫폼 무관 코어(순수 Dart) 구조 확정
  - AC: `lib/core/`에 ① 트래킹 버퍼링 ② 로컬 저장(기존 `TrackStore` 승격·일반화) ③ 업로드 재시도(지수 백오프) ④ 폴리라인 인코딩 모듈. 각 모듈에 유닛 테스트 존재. **`geolocator`/`flutter_foreground_task` 등 플랫폼 패키지 import 0건**(그 자체가 코어의 정의).
  - 의존: A-C1
- **A-C5 [flutter]** Android 전용 코드의 코어 침투 방지 규칙 확립
  - AC: 트래커 구현체를 별도 디렉토리(예: `lib/platform/android/`)로 분리하고, `lib/core/`에서 플랫폼 패키지 import를 금지하는 규칙을 문서화 + 가능하면 `analysis_options.yaml` 커스텀 lint/`import_lint`로 강제. 위반 시 analyze 실패.
  - 의존: A-C1~A-C4
- **A-C6 [flutter]** 기본 스캐폴드 정리 + 앱 골격 아키텍처 선정
  - AC: `main.dart`의 카운터 앱 제거, 빈 홈 셸 렌더. `test/widget_test.dart` 갱신(카운터 테스트 제거). **라우팅·상태관리·네트워킹 스택 선정**(planner 제안: 라우팅 `go_router` / 상태관리 `Riverpod` / 네트워킹 `dio` + 재시도 인터셉터 — 열린 질문 O-2 참조) 및 근거 1단락 문서화. `flutter analyze`·`flutter test` green.
  - 의존: 없음 (백엔드와 병렬 가능)

**배치 A 규모**: 백엔드 6 + 클라이언트 6 = **12개 항목**. 발급물 의존 0.
**병렬성**: 백엔드 트랙(A-B*)과 클라이언트 트랙(A-C*)은 완전 독립 — 동시 진행 가능. A-B5·A-C6은 domain-analyst/사용자 결정 선행이 이상적.

---

## 3. 배치 B — 서버측 도메인 + 화면 뼈대 (다음 배치, 윤곽만)

배치 A 완료 후 상세화. 전부 발급물 무관(카카오 실검증만 스텁).

- **[backend/domain]** User 애그리거트(닉네임·프로필·상태 ACTIVE/WITHDRAWN) + 탈퇴 처리(UserWithdrawn: 닉네임 익명화, kakao_id·device_token 파기, track_payload 삭제, rank_entry·리플레이 파생물 익명 보존).
- **[backend]** 앱-서버 토큰 체계: `KakaoTokenVerifier` **포트** 정의 + JWT 발급·갱신·만료(401)·서버 인증 필터. **판단: 카카오 앱 키 없이 구현 가능** — 실 카카오 검증 어댑터만 스텁/페이크로 두고(통합테스트는 페이크 verifier), M0 키 확보 후 실 어댑터만 배선. 카카오 회원번호는 인증 어댑터 밖으로 노출 금지.
- **[backend]** DeviceToken 엔티티 + 등록/갱신 **서버 API**(클라 토큰 취득·onTokenRefresh는 Firebase 필요 → M3, 대기).
- **[backend/domain]** Crew 컨텍스트 전체: Crew·CrewMember·InviteCode, 초대코드 전용 가입, 불변식(크루장 1명·최선임 자동 승계·마지막 1인 CLOSED), CrewMemberJoined 발행, 초대코드 생성 API.
- **[backend/domain]** Course·RaceSession·Participation 서버측: RoutePath VO, 코스 불변식, RaceSession 상태머신(DRAFT→OPEN→RUNNING→FINALIZING→COMPLETED/CANCELLED), upload_deadline NOT NULL, 취소 정책, STARTED 신호 API. (지도 그리기 UI는 대기.)
- **[flutter]** 화면 뼈대 UI(디자인 기준 `app/docs/design/…1a_라임.dc.html`): 홈, 크루 목록·상세, 크루 생성, **초대 코드 입력→참가 플로우**, 온보딩 닉네임, 설정/프로필, **앱 내 회원 탈퇴 UI**, 세션 상세. **제외(대기)**: 카카오 로그인 화면(키), 지도·코스 그리기(Client ID), 초대 링크 딥링크(도메인).
- **[flutter]** `app_min_version` 클라이언트 강제 업데이트 판단 로직(A-B6 엔드포인트 소비).
- **[test]** CI 구성(GitHub Actions: 빌드+테스트 자동) — 발급물 무관, 배치 B 말미 또는 A로 앞당김 가능.

---

## 4. 대기 목록 (M0 발급물 확보 시 착수 — 이번·다음 배치 제외)

| 작업 | 필요 발급물 |
|---|---|
| 카카오 로그인 연동(`kakao_flutter_sdk`, 로그인 화면) | 카카오 앱 키 + 키 해시 등록 |
| `flutter_naver_map` 연동, 코스 지도 그리기 UI | 네이버 지도 Client ID |
| FCM 클라이언트 수신(M3) / DeviceToken 클라 취득 / Crashlytics SDK 통합 | Firebase(`google-services.json`) |
| 초대 링크 딥링크/앱링크 + 미설치 랜딩 페이지 | 도메인(Cloudflare) |
| release 서명 연결(`signingConfig`)·AAB 실검증 | 릴리즈 keystore(M0) |
| dev/prod 환경 분리의 **키 값**(카카오 키·Firebase·지도 ID) | 위 발급물들 (구조·API base URL 골격은 배치 B에서 선반영 가능) |

---

## 5. 마일스톤 매핑

- 배치 A·B 전 항목 → **M1 (트래킹 스파이크 + 뼈대)**. 스파이크(1주차)는 이미 코드 완료 상태이므로, 이번 배치는 M1의 "아키텍처 확정 + 백엔드 뼈대 + 화면 뼈대" 구획을 담당한다.
- 대기 목록 중 FCM 클라이언트는 원래 **M3** 소속(계획서), 나머지는 M1이나 발급물 게이트로 보류.

---

## 6. 계획서 ↔ 코드/todolist 불일치 (업데이트 제안)

- `app/CLAUDE.md`의 "Project state"가 여전히 "카운터 스캐폴드 · 서드파티 의존성 없음"으로 기술 → 실제로는 스파이크 코드 + `geolocator`/`flutter_foreground_task`/`path_provider` 도입됨. **제안**: `app/CLAUDE.md` 갱신(불일치, 사용자/오케스트레이터 확인 후 반영).
- 계획서 §5.1/§6 내부 상충(합류·지급 "알림" 처리 방식) → 아래 O-1로 에스컬레이션.

---

## 7. 열린 질문 (사용자/오케스트레이터 결정 필요)

- **O-1 (계획서 내부 상충)** CrewMemberJoined·RewardGranted의 "알림" 처리: 계획서 §5.1은 "합류→알림 등", §6은 "FCM은 2종만(리마인더·리플레이)". **인앱 표시로 갈음** vs **FCM 확장** vs **범위 외 확정** 중 결정 필요. 배치 A는 무관, 이벤트 소비(배치 B의 Crew, M3의 Reward) 착수 전까지 확정 요망.
- **O-2 (계획서 미기재 — 기술 선정)** 클라이언트 라우팅·상태관리·네트워킹 스택. planner 제안: `go_router` + `Riverpod` + `dio`. 이의 없으면 이 조합으로 A-C6 확정, 선호가 있으면 회신 요망. (배치 A 진행의 유일한 선결 결정.)
- **O-3 (확인)** MySQL 버전(8.x 가정)·Flyway 채택(계획서 "Flyway 등")·Java 25 toolchain 로컬 가용성 확인. 이견 없으면 위 기본값으로 진행.

> 위 3건 외 배치 A를 막는 결정 없음. O-2만 A-C6의 선결 사항이며, planner 제안 기본값으로 진행 가능(사용자 override 시 해당 항목만 수정).
