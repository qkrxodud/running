# 러닝 크루 리플레이 앱 — TODO 리스트

> 기준 문서: [러닝크루_앱_계획서.md](러닝크루_앱_계획서.md)
> 현재 상태: Flutter 기본 스캐폴드(`lib/main.dart` 카운터 앱), backend 디렉토리 비어 있음
> 원칙: Android 우선 출시, 로컬 우선 저장("지연은 있어도 유실은 없게"), 신뢰 기반 그룹(안티치트 배제)

---

## M0 — 스토어·인프라 준비 (즉시)

### 스토어 / 계정
- [ ] Google Play Console 개발자 계정 생성
- [ ] 패키지명(applicationId) 확정 — 현재 `com.example.running`이므로 **반드시 변경** (`android/app/build.gradle` + `MainActivity` 패키지 경로)
- [ ] 릴리즈 서명 키(keystore) 생성 + 안전한 백업 (분실 시 앱 업데이트 불가)
- [ ] Android 개발자 인증 완료 — 패키지명 + 서명 키 SHA-256 등록 (**2026년 4월부터 필수**)

### 카카오 개발자 콘솔
- [ ] 카카오 개발자 콘솔 앱 생성
- [ ] Android 플랫폼 등록 (패키지명 + 키 해시 — 디버그/릴리즈 둘 다)
- [ ] 크루원 카카오 계정 **팀원 등록** (테스트 단계에서는 팀원만 로그인 가능 — M4까지 이 방식 유지)
- [ ] 프로덕션 전환 전 동의항목 검수 요건 확인·정리 (동의항목 목록은 검수 요건 확인 시 확정)

### 지도 / Firebase
- [ ] 네이버 클라우드 플랫폼 콘솔에 지도 SDK 앱 등록 (Client ID 발급)
- [ ] Firebase 프로젝트 생성 — FCM + Crashlytics 활성화, `google-services.json` 준비

### 도메인 / 홈서버 인프라
- [ ] 도메인 확보 및 Cloudflare 등록
- [ ] Cloudflare Tunnel(cloudflared) 구성 — 홈서버 → `api.<도메인>` 서브도메인, HTTPS 확인
- [ ] 홈서버 Docker + Docker Compose 환경 준비
- [ ] cloudflared 서비스(systemd 등) 등록 — 재부팅 시 자동 복원

### 법적 문서
- [ ] **개인정보처리방침 초안 작성** — 위치정보 수집 목적·보관 기간 명시
  - [ ] 탈퇴 시 파기 정책 명시: "식별 정보(닉네임·카카오 키·푸시 토큰)와 위치 원본은 파기, 식별자가 분리된 파생물(리플레이 스냅샷 내 경로)은 익명 보존"
- [ ] 이용약관 초안 작성
- [ ] 방침·약관 페이지 Cloudflare Pages 무료 호스팅 배포 (Play 등록 시 URL 필수)
- [ ] Play Console 데이터 보안(Data Safety) 양식 항목 사전 정리 (위치 수집 앱 기준)
- [ ] 웹 계정 삭제 요청 페이지 (방침 페이지에 병설, Cloudflare Pages) — **Play 계정 삭제 요건**은 앱 내 탈퇴 UI + 웹 URL 둘 다 요구

---

## M1 — 트래킹 스파이크 + 뼈대 (2~3주)

### 1주차: 기술 스파이크 ⚠️ 프로젝트 성립 조건 — 다른 무엇보다 먼저
- [x] 로그인·화면 없이 빈 앱 + Foreground Service(type: location)만으로 위치 기록 구현 (2026-07-04, `lib/spike/` + `lib/core/tracking/`)
  - [x] `geolocator` + `flutter_foreground_task` 조합, `ACCESS_BACKGROUND_LOCATION` **선언 없이** 구성 (Play 백그라운드 위치 심사 우회)
- [ ] 갤럭시 실기기에서 **화면 끈 채 1시간 이상** 백그라운드 기록 검증 — 절차: `app/docs/m1_spike_검증절차.md`
- [ ] 실주행 1회 검증 (실제 달리기, 주머니에 폰)
- [ ] 절전 모드·앱 자동 종료 상황에서 유실 정도 확인
- [ ] **판정**: 유실이 심하면 이 시점에 유료 패키지(`flutter_background_geolocation`) 전환 결정 (fail fast)

### 클라이언트 아키텍처 확정 (스파이크 통과 후)
- [x] `LocationTracker` 인터페이스 정의 (start/stop/pause/resume, 포인트 스트림, 상태) — 격리 지점 ① (2026-07-04, `lib/platform/location/`)
- [x] `NotificationService` 인터페이스 정의 — 격리 지점 ② (2026-07-04)
- [x] `PermissionService` 인터페이스 정의 — 격리 지점 ③ (2026-07-04)
- [x] 플랫폼 무관 코어를 순수 Dart로 설계: 버퍼링·로컬 저장·백오프 재시도 큐·폴리라인 인코딩 (2026-07-04, `lib/core/` — HTTP 실전송 배선은 배치 B)
- [x] Android 전용 패키지는 트래커 구현체 내부로만 한정하는 규칙 확립 (allowlist 가드 테스트 `test/core/no_platform_imports_test.dart`, R-002)
- [x] 기본 스캐폴드 정리: `main.dart` 라우터 기반 재구성, go_router + Riverpod + dio 선정 (2026-07-04)

### 화면 뼈대 (사용자 흐름 7단계가 끊기지 않도록)
- [x] 홈 화면 — 로그인 후 착지, 내 크루 목록 (1a 라임 디자인. 예정 레이스 표시는 Race 컨텍스트 B2에서) (2026-07-04 B1)
- [x] 크루 목록·크루 상세 화면 (멤버 목록 — O-1 인앱 갈음 반영. 예정 세션은 B2) (2026-07-04 B1)
- [x] 크루 생성 UI (2026-07-04 B1)
- [x] **초대 코드 입력 → 크루 참가 플로우** (클라이언트 + 참가 API) (2026-07-04 B1)
- [ ] 초대 링크 **딥링크/앱링크 처리**: 카톡에서 링크 탭 → 앱 열림 → 코드 자동 적용
  - [ ] 미설치 시 랜딩 페이지 골격 (Cloudflare Pages) — **opt-in 링크 삽입은 M4 트랙 등록 후에야 가능**, 디퍼드 딥링크는 2차(M5)
- [x] 최초 로그인 닉네임 설정 온보딩 (onboarded_at 기준 게이트) (2026-07-04 B1)
- [x] 설정/프로필 화면: 닉네임 수정, 로그아웃 — 방침·약관 링크는 URL 확보 후 연결(placeholder) (2026-07-04 B1)
- [x] **앱 내 회원 탈퇴 UI** — 2단 확인 + "재로그인 시 신규 계정, 복구 불가" 고지 (2026-07-04 B1)
- [x] 세션 상세 화면: 일시·코스 미리보기·참가자 상태·보상·"지금 뛰는 중" 표시, '레이스 시작' 진입점 + 세션 목록 화면 (2026-07-04 B2)

### 백엔드 뼈대
- [x] Spring Boot 3.5+ + Java 25 프로젝트 생성 (`running/backend`) (2026-07-04, Gradle 9.1 + foojay 툴체인)
- [x] 헥사고날 구조 + 컨텍스트별 패키지 분리 (user / crew / race / tracking / ranking / reward / replay + common) (2026-07-04)
- [x] Docker Compose 구성 (Spring Boot + MySQL 8, restart: unless-stopped, healthcheck) (2026-07-04)
- [x] MySQL 스키마 마이그레이션 도구 도입 — Flyway + Testcontainers 라이브 마이그레이션 테스트 (R-003 박제) (2026-07-04)
- [x] 계획서 §7 데이터 모델 기반 초기 마이그레이션 작성 — `V1__init.sql` 17테이블, `rank` 백틱(R-003) (2026-07-04)
- [x] REST API 계약 문서 v0.1 — conventions/app-version/crew/session (`docs/contracts/`). 명령 상세·나머지 조회(결과/순위, 히스토리·PB)는 배치 B에서 확장 (2026-07-04)
- [x] 시간대 규약 확정: JVM·MySQL 세션·jackson 3중 UTC 고정, 표시 KST는 클라 소관 (2026-07-04)

### 인증 + User 컨텍스트
- [ ] 카카오 로그인 **실연동** (`kakao_flutter_sdk` — M0 앱 키 대기. 서버 KakaoTokenVerifier 포트 + local/dev 스텁 + prod fail-fast 는 완료, 2026-07-04 B1)
- [x] 앱-서버 토큰 체계: JWT HS256 액세스 30분/리프레시 30일 쌍회전, AUTH_* 401 세분, per-request status 조회, kakao_id 어댑터 봉인 (2026-07-04 B1)
- [x] User 애그리거트: 닉네임, 상태(ACTIVE/WITHDRAWN), onboarded_at(V2) (2026-07-04 B1)
- [x] DeviceToken 엔티티 + 등록/갱신 서버 API — 클라 측 취득·갱신은 M3 (2026-07-04 B1)
- [x] 탈퇴 처리(UserWithdrawn 이벤트) — 6단계 단일 TX, 라이브 검증 (2026-07-04 B1):
  - [x] 닉네임 익명화("탈퇴한 러너"), kakao_id·device_token 즉시 파기
  - [x] track_payload(raw/refined) 지체 없이 삭제 (정리 리스너)
  - [x] rank_entry·리플레이 파생물은 익명 보존

### Crew 컨텍스트
- [x] Crew 애그리거트: 이름, 크루장, 멤버 목록, 상태 (2026-07-04 B1)
- [x] CrewMember: 역할(LEADER/MEMBER), 가입일, 상태 + 재가입 복원 의미론 (2026-07-04 B1)
- [x] InviteCode: 만료 시각 + 사용 횟수 제한(1~100), 초대 코드로만 가입 — 골든 34케이스 (2026-07-04 B1)
- [x] 초대 코드 생성 API + 크루장 발급·복사 UI (2026-07-04 B1) / [ ] 카톡 공유 플로우 (카카오 키·도메인 대기)
- [x] 불변식: 크루장 항상 1명 — 승계 정책(joined_at 최선임, 동률 시 id 오름차순) 골든+통합 테스트 (2026-07-04 B1)
- [x] 마지막 1인 탈퇴 시 크루 CLOSED 처리 (2026-07-04 B1)
- [x] CrewMemberJoined 이벤트 발행 (소비자는 인앱 갈음이라 없음 — 번복 시 소비자만 추가) (2026-07-04 B1)
  - [x] 합류 "알림" 처리 방식 결정 — **인앱 표시로 갈음 확정** (O-1, 사용자 결정 2026-07-04. RewardGranted 동일)

### Course + RaceSession (Race 컨텍스트)
- [x] Course 애그리거트: RoutePath(1e5 폴리라인, 서버 거리 확정), 출발/도착, 생성자(크루장 전용) — **불변 애그리거트로 격상**(수정 API 미노출) (2026-07-04 B2)
- [ ] `flutter_naver_map` SDK 연동 (**Client ID 대기** — CoursePolylineMap 인터페이스 + placeholder 구현 완료, 어댑터 1개만 추가하면 되는 구조) (구조 2026-07-04 B2)
- [ ] 코스 등록 ① 지도에서 경로 그리기 (**Client ID 대기** — dev 시드 코스 + 코스 선택 목록으로 우회 중) — 승격 방식(②)은 M2
- [x] 불변식: 발행된 코스는 불변 — 구조적 보장(수정 API 부재) + OPEN 발행 잠금 (2026-07-04 B2)
- [x] RaceSession 애그리거트 (2026-07-04 B2):
  - [x] 상태머신: DRAFT → OPEN → RUNNING → FINALIZING → COMPLETED / CANCELLED — 24셀 매트릭스 골든 박제, OPEN은 명시 발행, RUNNING은 첫 STARTED 유발
  - [x] `upload_deadline` NOT NULL — "+12시간"은 앱 레이어 UX 기본값으로 구현
- [x] 세션 생성 UI: 코스 선택(시드)·일시·보상 텍스트·마감 (2026-07-04 B2)
- [x] Participation: REGISTERED/STARTED/DNS/WITHDRAWN 구현 + enum 6값 집합 보존 (FINISHED/DNF 전이는 M2 업로드 파이프라인) (2026-07-04 B2)
- [x] 취소 정책: DRAFT/OPEN/RUNNING → CANCELLED, 순위·보상 미생성 (뛰던 트랙 개인 기록 보존은 M2 트랙 구현 시) (2026-07-04 B2)
- [x] '레이스 시작' STARTED 신호 1회 + "지금 뛰는 중" 표시 (트래킹 배선은 M2 — W26-1 정본: 활성 버튼) (2026-07-04 B2)

### 개발 환경 / 품질 기반
- [x] dev/prod 환경 분리 (dart-define, `config/{dev,prod}.json`): API base URL + 카카오 키·지도 Client ID 주입 자리, `/spike`·dev 로그인 prod 차단 (2026-07-04 B2)
- [ ] release 빌드 서명 연결 (`signingConfig`) + R8/ProGuard keep 규칙(카카오·네이버지도 SDK) + AAB 릴리즈 빌드 실검증 — M4 직전에 처음 하면 반드시 사고
- [ ] Crashlytics SDK 앱 통합 (`firebase_crashlytics` 초기화, 비치명 오류 로깅) — M0은 콘솔 생성, M4는 확인 루틴뿐이라 구현 단계가 여기
- [ ] versionCode/versionName 증가 규칙 (`app_min_version` 강제 업데이트 체계의 전제)
- [x] CI 구성 (GitHub Actions `.github/workflows/ci.yml`): flutter analyze+test / backend gradle build(Testcontainers) 2잡, main push·PR 트리거 (2026-07-04 B2)

### 운영 최소 장치
- [x] `GET /app-version` 최소 지원 버전 API (`app_min_version` 테이블) — 서버 구현 + 계약 일치 라이브 검증 완료 (2026-07-04)
- [x] 클라이언트 강제 업데이트 판단 로직 (앱 시작 시 호출, 차단 화면) — appVersion 하드코딩은 package_info_plus 도입 시 교체 (2026-07-04 B1)

---

## M2 — 트래킹 완성 (2~3주) ⚠️ 리스크 최대 구간

### 트래킹 클라이언트
- [ ] `AndroidForegroundTracker` 구현 (Foreground Service + 상시 기록 알림)
- [ ] TrackPoint 수집: (timestamp, lat, lng, altitude, speed, accuracy)
- [ ] **적응형 샘플링**: 주행 중 3~5초 / 정지(속도 0) 지속 시 10초 완화 / 이동 재개 시 복귀
- [ ] 타임스탬프는 기기 시계가 아닌 **GPS 시각 우선** 사용
- [ ] 로컬 우선 저장 (레이스 중 서버 없이도 완결 — 완주 후 사후 업로드)
- [ ] 클라이언트 로컬 상태머신: READY → RUNNING → FINISHED_LOCAL → UPLOADED
- [ ] 업로드: 인코딩 폴리라인 + 병렬 배열(timestamp/speed), 실패 시 지수 백오프 재시도
- [ ] **업로드 실패율 로깅 계측** (Crashlytics/구조화 로그에 심기 — M4 "확인 루틴"이 볼 데이터 소스, 계측 없으면 관측 불가)
- [ ] `client_meta`(os, os_version, device_model) 부가 수집 — 플랫폼 종속 필드는 스키마에 두지 않음

### 레이스 규칙 구현
- [ ] **그로스 타임**: 자동 일시정지 없음 (신호등 대기 포함)
- [ ] **기록 구간 = 시작 버튼 ~ 도착점 반경 최초 진입 시각 자동 확정** (종료 버튼 늦게 눌러도 손해 없음)
- [ ] 종료 버튼 = 트래킹 중단 + 업로드 트리거만 (도착점 미진입 상태 종료 → DNF 후보)
- [ ] 출발은 각자 시작 버튼 기준 (동시 출발 강제 없음, 개인 소요 시간으로 순위)

### 레이스 화면 / 엣지케이스
- [ ] 레이스 진행 중 화면: 경과 시간, 거리, 현재 페이스, 지도, 종료 버튼
- [ ] 레이스 시작 전 사전 점검: 위치 권한·위치 서비스 켜짐·GPS 수신 확인 후 시작 허용 — 시작 후 발견하면 기록 전체 무효
- [ ] 레이스 중 앱/프로세스 강제종료 후 재실행 복구: 진행 중 트래킹 감지 → 이어가기 또는 그 시점까지로 종료 처리 (제조사 강제 종료 리스크의 클라이언트 측 절반)
- [ ] 동일 세션 중복 시작 방지: 이미 STARTED/FINISHED_LOCAL 상태에서 '레이스 시작' 재시도 시 멱등 또는 거부
- [ ] 업로드 실패 잔존 기록 표시 + **수동 재시도 버튼** — 백오프 소진·앱 종료 후에도 마감 전 업로드 가능하게 (없으면 유실 직행)
- [ ] 종료 직후 결과 대기 화면: "업로드 완료 — 전원 완료 대기 중 (n/m명)"
- [ ] 서버 다운/점검 시 공통 에러·오프라인 UX — 홈서버 전제상 다운은 예정된 상황

### Tracking 서버 (데이터 심장부)
- [ ] TrackRecord 애그리거트: 시작/종료 시각, 총 거리, 총 시간
- [ ] 저장 분리: `track_record`(메타·요약) + `track_payload`(raw/refined 블롭) **1:1 테이블** — 조회는 track_record만, 블롭 접근은 리플레이 생성·재정제 전용 리포지토리 경로로 한정
- [ ] TrackUploadService: 업로드 수신, 폴리라인 디코딩, 기본 검증(시간 정합성) — 코스 이탈 검증은 FinishPolicy로 일원화, 여기서 중복 구현 금지
- [ ] TrackRefinementService (GPS 정제):
  - [ ] accuracy 임계 초과 포인트 제거
  - [ ] 이동 평균 스무딩
  - [ ] 이상 점프 보정
  - [ ] 총 거리·페이스는 **정제 후 좌표로 계산** (원시 하버사인 누적 금지 — 거리 부풀림)
  - [ ] 원시 + 정제 결과 모두 보존 (재정제 가능하도록)
  - [ ] GPS 유실 구간(샘플 공백) 식별 — 리플레이 보간 표시용 메타 포함 (리스크 표: 기록 유실 대응)
- [ ] TrackSegment: 구간(예: 500m 단위 — 확정값 아님, 튜닝 대상) 페이스 요약 — 리플레이 색상용
- [ ] TrackUploaded 이벤트 → Race 컨텍스트 통지

### FinishPolicy (완주 판정) — 순수 함수
- [ ] 조건 ① 도착점 반경 30m 이내 진입
- [ ] 조건 ② 정제 후 주행 거리 ≥ 코스 거리의 90%
- [ ] 조건 ③ 코스 일치율: 정제 포인트의 80% 이상이 코스 폴리라인에서 50m 이내
- [ ] 세 조건 모두 충족 시 완주, 미충족 시 DNF — 단 기록·경로는 보존해 리플레이에 표시
- [ ] 임계값(30m/90%/80%/50m)을 설정값으로 외부화 (운영 튜닝 대상)
- [ ] 코스 이탈 검증은 FinishPolicy로 일원화

### 세션 마감 처리 (Race 컨텍스트 소속 — Participation 상태 변경·RaceCompleted 발행 책임)
- [ ] RaceCompleted 트리거: 전원 업로드 완료 **또는** upload_deadline 도달 (스케줄러)
- [ ] 마감 도달 시 미처리자 정리: STARTED 후 미업로드 → **DNF**, 출주하지 않은 REGISTERED → **DNS**(신청 후 미출주)

### Ranking 컨텍스트
- [ ] RankingPolicy: 완주 기록 오름차순, **동률은 공동 순위 + 다음 순위 건너뜀** (1, 1, 3위)
- [ ] DNF/DNS는 순위 미부여, 목록 하단 표기
- [ ] RaceResult / RankEntry (순위, 기록, 평균 페이스, PB 여부) 확정 + ResultFinalized 이벤트
- [ ] PersonalBest: 유저×코스(course_id) 기준 (코스 불변이 전제 — 거리대 기준 PB는 2차)
- [ ] 순위표·기록 히스토리 화면: 세션별 순위, 개인 누적 기록, PB
- [ ] 코스 등록 ② 과거 주행 기록을 코스로 승격 (M1에서 이동 — TrackRecord 구현 후에야 가능)

### 테스트 (이 마일스톤의 핵심 산출물)
- [ ] FinishPolicy 골든 테스트 (지름길 주파 / 다른 길 오진입 / 정상 완주 케이스)
- [ ] TrackRefinement 골든 테스트
- [ ] RankingPolicy 테스트 (동률, DNF/DNS 혼합)
- [ ] **실주행 원시 트랙을 테스트 픽스처로 축적** — 정제 알고리즘 튜닝 회귀 방지선
- [ ] 갤럭시 중심 실기기 테스트: 절전 모드, 앱 자동 종료, 화면 꺼짐 장시간
- [ ] 실기기 **다종** 테스트 — 갤럭시 외 기종 확보해 기기별 GPS 편차 확인 (리스크 표: 백그라운드 GPS 기기별 불안정)

### 온보딩 UX
- [ ] 배터리 최적화 예외 안내 (삼성 앱 자동 종료 대응)
- [ ] 위치·알림 권한 요청 플로우 (PermissionService 경유)

---

## M3 — 리플레이·알림 (2주) — 제품의 얼굴

### ReplaySnapshot 생성 (서버, 독립 패키지 `replay`)
- [ ] `ReplayGenerationService` + `ReplaySnapshotRepository` — 별도 애그리거트 없는 프로젝션 계층
- [ ] 전원 트랙을 **각자 시작 시점 t=0 상대 시각으로 정렬·병합**
  - [ ] 정렬·병합 로직 골든 테스트 (입력 트랙 → 기대 스냅샷 고정 — 추월 계산과 별개, 계획서 테스트 전략의 "ReplaySnapshot" 대상)
- [ ] 추월 지점 계산(동일 진행거리 도달 시각 비교) — **서버 사전 계산**, 순수 함수 + 골든 테스트
- [ ] 구간 페이스 색상 데이터 포함
- [ ] `schema_version` 필드 + 생성 상태(GENERATING → READY / FAILED)
- [ ] **ResultFinalized 커밋 후 비동기 생성** (AFTER_COMMIT 이벤트 리스너 — MVP 규모에선 잡 큐 불요)
- [ ] FAILED 상태 관측 가능 + 재시도 경로
- [ ] **삭제 → 최신 스키마 재생성(멱등) 운영 도구** (관리 API 또는 스크립트) — 처음부터 마련
- [ ] `replay_snapshot` 테이블 저장 (일일 DB 백업에 자동 포함)
- [ ] 리플레이 JSON 조회 REST API
- [ ] **리플레이 조회 이벤트 로깅** — M4 성공 기준(레이스 후 24시간 내 조회율 80%) 측정 수단, 없으면 성공 기준 판정 불가

### 리플레이 뷰어 (클라이언트)
- [ ] 스냅샷 하나로 렌더링: 마커 보간 애니메이션 (전원 동시 이동)
- [ ] GPS 유실 구간 보간 표시 (실측 구간과 구분되게 — 리스크 표 대응)
- [ ] 배속 재생
- [ ] 시간축 슬라이더
- [ ] 추월 지점 마킹
- [ ] 구간 페이스 색상 표시
- [ ] DNF 참가자 경로도 표시 (기록 보존 정책)
- [ ] 뷰어 폴리싱 (제품의 얼굴 — 완료 기준을 구체화): 배속 포함 마커 애니메이션 프레임 드랍 없음 / 로딩·GENERATING·FAILED 상태별 UI / 10명 × 600포인트 동시 렌더 성능 확인
- [ ] 세션 결과 화면: 순위, PB 뱃지, DNF/DNS 하단 표기, 리플레이 진입 버튼 (누적 히스토리 화면과 별개인 세션 단위 화면)

### FCM 알림 (2종만)
- [ ] 세션 리마인더 (예정 시각 전)
  - [ ] 리마인더 **발송 스케줄러** (서버 — 예정 시각 전 트리거. 마감 스케줄러와 별개)
- [ ] 결과 확정 "리플레이가 열렸어요" — 최초 READY 도달 시 트리거
  - [ ] **세션당 1회 멱등** (`race_session.replay_notified_at` 기록, 재생성 READY 시 재발송 금지)
- [ ] FCM **클라이언트 수신 처리**: `firebase_messaging` 연동, Android 알림 채널 생성, 포그라운드/백그라운드/종료 상태별 수신, 토큰 갱신(onTokenRefresh → 서버 반영)
- [ ] 알림 탭 → 해당 세션/리플레이 화면 **딥링크** — "조회율 80%" 목표의 전제(알림에서 뷰어 직행)

### Reward 컨텍스트
- [ ] RewardPlan / RewardItem: "1등: 커피, 2등: 음료, 3등: 사탕" — 자유 텍스트 + 예상 금액
- [ ] ResultFinalized → RewardGrant 생성 (PENDING)
- [ ] 지급 상태 장부: PENDING / SENT / CONFIRMED — 크루장이 수동 지급 체크 ("누가 누구에게 무엇을")
- [ ] 보상 지급 체크 UI (크루장용 — 장부 상태 변경 화면, 사용자 흐름 7단계의 완결 조건)
- [ ] RewardGranted 이벤트 발행
  - [ ] 지급 알림 처리 방식 결정 — "FCM은 2종만" 원칙과 상충(CrewMemberJoined와 같은 케이스): 인앱 표시로 갈음할지 FCM 확장할지

---

## M4 — 비공개 테스트 = 실전 운영 (4주)

### 배포
- [ ] Play 비공개 테스트 트랙 등록 (스토어 등록정보, 스크린샷, 방침 URL, 데이터 보안 양식)
- [ ] 지인 배포 + **예비 테스터 확보** — 프로덕션 승인 요건: 테스터 12명 이상, 14일 연속 참여
- [ ] 테스터에게 14일간 설치 유지 안내

### 운영 체계 가동
- [ ] MySQL 일일 덤프 → 로컬 외 위치 보관 (클라우드 스토리지 또는 별도 기기) + 복원 리허설 1회
- [ ] uptime 모니터 (Cloudflare 헬스 알림 또는 외부 모니터)
- [ ] 서버 구조화 로그 + 디스크 사용량 체크
- [ ] Crashlytics 크래시 모니터링 + 업로드 실패율 로깅 대시보드 확인 루틴
- [ ] 정전 복구 무인 복원 실제 테스트 (전원 차단 → 자동 재기동 확인)

### 운영 (성공 기준 측정 기간)
- [ ] 주 1회 레이스 × 4주 운영, 보상 수동 지급
- [ ] 피드백 수집 (특히: 그로스 타임 vs 네트 타임, 배터리, 기록 유실)
- [ ] **성공 기준 측정**: 크루 6~10명이 4주 자발 지속 + 레이스 후 24시간 내 리플레이 조회율 ≥ 80%
- [ ] 판정 임계값(30m/90%/80%/50m) 실데이터 기반 튜닝
- [ ] 검증 결과로 2차(실시간 공유·게임성) 투자 여부 결정

---

## M5 — 게임성·자동화 (MVP 검증 후, 2차)

- [ ] 실시간 위치 공유: 달리는 중 위치 브로드캐스트 (WebSocket(STOMP) 또는 SSE + Redis pub/sub)
- [ ] 비동기 레이스: 기간 내 각자 뛰고 기록 경쟁 (상대 시각 동기화 구조라 리플레이 변경 없음, upload_deadline 3일/7일)
- [ ] 핸디캡 시스템: 출발 시차형 / 기록 보정형
- [ ] 벌칙/보너스 룰: 꼴등 벌칙, PB 갱신 보너스 등 크루별 커스텀
- [ ] 기프티콘 API 연동(기프티쇼 비즈 등) — 발송 어댑터 + **Outbox 패턴**(지급 확정 트랜잭션과 외부 발송 분리, 중복 발송/유실 방지)
- [ ] 실시간 랭킹 필요 시 Redis Sorted Set 도입
- [ ] 거리대(5km/10km) 기준 PB
- [ ] 네트 타임/자동 일시정지 재검토 (크루 피드백 기반)
- [ ] 디퍼드 딥링크: 미설치 → 설치 후 초대 코드 자동 적용 (M1 랜딩 페이지의 2차분)
- [ ] 카카오 로그인 프로덕션 전환 (동의항목 검수)
- [ ] **공개 전환 게이트: LBS 사업자 신고 완료** (신고 전까지 비공개 트랙 유지)
- [ ] 공개 전환 시 서버 이전 검토: 컨테이너 그대로 저비용 VPS로 (Oracle Free Tier / Lightsail / Hetzner) — Cloudflare 뒤라 클라이언트 수정 없이 원점 교체

---

## M6 — iOS 확장 (수요 확인 후, 목표 2~3주)

- [ ] `IosBackgroundTracker` 구현 (Always 권한 + 위치 백그라운드 모드) — 코어 변경 없이 구현체만 추가
- [ ] Always 권한 온보딩 플로우
- [ ] 카카오 로그인 iOS 설정
- [ ] Apple Developer 계정 + TestFlight 배포 채널
- [ ] 네이버 지도 iOS 동작 확인

---

## 상시 점검 (모든 마일스톤 공통)

- [ ] 컨텍스트 간 결합은 이벤트로만: TrackUploaded → 정제 → 완주 판정 → RaceCompleted → ResultFinalized → (리플레이 생성 / RewardGrant)
- [ ] 도메인 계층 TDD, 어댑터(업로드 API, FCM)는 최소한의 통합 테스트
- [ ] Android 전용 코드가 코어(순수 Dart)에 침투하지 않는지 리뷰
- [ ] 업로드 스키마 변경 시 `app_min_version` 갱신으로 구버전 차단
- [ ] 스키마 변경이 과거 리플레이(추억 아카이브)를 깨지 않는지 확인 — 깨지면 schema_version 올리고 재생성

## 범위 외 — 만들지 않기로 한 것 (스코프 크리프 방지)

- 부정행위 탐지 (GPS 스푸핑, 이동수단 판별)
- 공개 크루 검색·모집, 소셜 피드
- 참가비 징수 (사행성 이슈)
