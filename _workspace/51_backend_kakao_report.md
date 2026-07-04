# 51 — 백엔드: 카카오 로그인 실연동(서버 측) 리포트

> 작성: backend-dev · 2026-07-05 · 기준: 12_analyst_design_B.md §2, auth-api.md, backend-hexagonal 스킬
> 과제: 실 카카오 토큰 검증 어댑터(RealKakaoTokenVerifier) + 위임형 배선 + 테스트 + prod 부팅 가능화

## 1. 구현 내역

### 신규 (backend/src/main/java/com/runningcrew/user/adapter/out/kakao/)
- **RealKakaoTokenVerifier** — `GET {base-url}/v2/user/me` Bearer 호출 → `id`(카카오 회원번호) → `KakaoAccount(String)`.
  - RestClient(SimpleClientHttpRequestFactory, 연결 3s / 응답 5s).
  - kapi 401·400 → `KakaoTokenInvalidException`(→ `AUTH_KAKAO_TOKEN_INVALID`, AuthService 기존 catch 경로).
  - kapi 5xx·타임아웃·연결 실패 → 동일 `KakaoTokenInvalidException`이되 **WARN 로그로 장애 원인 구분 기록**(502 별도 코드는 §4 보류).
  - id 누락/빈 응답 → `KakaoTokenInvalidException`.
- **DelegatingKakaoTokenVerifier** — 토큰이 `stub:` 접두면 StubKakaoTokenVerifier, 아니면 Real로 위임(스텁·실 공존).
- **KakaoVerifierConfig** — 프로필별 `KakaoTokenVerifier` 빈 **정확히 1개** 노출(주입 모호성 없음):
  - `local/dev/sandbox` = Delegating(new Stub + Real)
  - `prod` = Real 단독
- **KakaoProperties** — `@ConfigurationProperties(prefix="kakao")`, base-url·타임아웃만 외부화(앱 키 없음).
- **KakaoUserResponse** — kapi user/me 응답 중 `id`만 매핑(`@JsonIgnoreProperties(ignoreUnknown=true)`).

### 변경
- **StubKakaoTokenVerifier** — `@Component/@Profile` 제거(독립 빈 아님). 로직 불변, 이제 Delegating 내부에 주입됨.
- **application.yml** — `kakao.{base-url,connect-timeout,read-timeout}` 블록 추가(env 오버라이드). prod 프로필 주석 갱신(빈 부재 fail-fast → Real 배선으로 부팅 가능).

### 앱 키 필요 여부
- **불필요.** `/v2/user/me`는 사용자 액세스 토큰(Bearer)만으로 호출된다. 서버에 저장할 카카오 자격(Admin/REST 키)이 없다. 외부화 설정은 base-url·타임아웃뿐(env: `KAKAO_API_BASE_URL`, `KAKAO_CONNECT_TIMEOUT`, `KAKAO_READ_TIMEOUT`).

## 2. 계약 대비
- auth-api.md §1(login) 무변경 준수: 성공 200(자체 JWT), 실패 `401 AUTH_KAKAO_TOKEN_INVALID` / `400 VALIDATION_ERROR`. 포트 뒤 교체이므로 §1~§3 계약 코드·shape 변화 없음.
- §4(스텁 모드)는 삭제하지 않았다 — 위임 구조로 dev/sandbox에서 스텁이 **계속 유효**하기 때문. (계약상 "실 배선 시 §4 삭제" 문구는 스텁 전용 시절 전제 → 실제로는 공존 배선을 택함. auth-api.md §4 문구 최신화는 domain-analyst 판단 대상 — §4 보류 참조.)

## 3. 테스트 (전부 그린, ./gradlew build 통과)
- `RealKakaoTokenVerifierTest`(5) — JDK 내장 HttpServer로 kapi 목킹(실 HTTP+실 타임아웃, 외부 의존 무추가):
  정상 id 추출 & Bearer 전달 / 401 → invalid / read timeout → invalid / 5xx → invalid / id 누락 → invalid.
  (MockWebServer·WireMock 미도입 사유: 타임아웃 재현엔 실 소켓이 필요 — 표준 HttpServer가 dep 없이 이를 충족.)
- `DelegatingKakaoTokenVerifierTest`(3) — stub:→스텁, 무접두→Real, `stub:`(빈 id)→스텁 거부.
- `ProdProfileContextTest`(2, 신규) — prod 프로필 Testcontainers 부팅 성공 + `KakaoTokenVerifier` == RealKakaoTokenVerifier + DevCourseSeeder 미주입.
- `SandboxProfileContextTest`(수정) — 활성 빈이 DelegatingKakaoTokenVerifier임으로 갱신.

## 4. 빌드·라이브 결과
- `./gradlew build`: **BUILD SUCCESSFUL** (신규 12테스트 포함 전체 그린).
- bootRun(local, 포트 8090 — 8080은 기존 프로세스 점유, sandbox 8081 미접촉):
  - `stub:kakao-live-check-1` → **200**, is_new_user=true, JWT 발급 정상.
  - `totally-fake-real-token-xyz`(무접두 → 실 kapi 호출) → **401 `AUTH_KAKAO_TOKEN_INVALID`**.

## 5. prod 부팅 가능 여부 변화
- **이전**: prod에 `KakaoTokenVerifier` 빈 부재 → 포트 주입 실패로 의도적 fail-fast(부팅 불가).
- **이후**: RealKakaoTokenVerifier(@Profile prod)가 포트를 채워 **prod 부팅 가능**. ProdProfileContextTest가 박제. JWT_SECRET env 필수 요건은 불변(JwtSecretFailFastTest 그대로 유효).

## 6. 보류 / 후속 (domain-analyst 판단 필요)
- **보류-1 (kapi 장애용 오류 코드)**: 현재 kapi 5xx·타임아웃도 `AUTH_KAKAO_TOKEN_INVALID`(401)로 매핑(계약 login 허용 코드가 이것뿐 + "새 code 추가 금지" 지시 준수). 의미상 "재로그인" 유도라 장애 시엔 부정확 — 별도 `AUTH_KAKAO_UNAVAILABLE`(502, "잠시 후 재시도") 코드를 계약(conventions §4 + auth-api §1)에 추가할지 **domain-analyst 결정 요청**. 추가 시 RealKakaoTokenVerifier의 장애 경로만 신규 예외로 분기(WARN 로그 지점 이미 분리됨) + GlobalExceptionHandler 매핑 1건.
- **보류-2 (auth-api §4 문구)**: "실 배선 시 §4 삭제" 문구는 스텁 전용 전제 → 실제는 공존 배선. §4를 "dev/sandbox 공존 유지"로 최신화할지 domain-analyst 판단.
- **보류-3 (실 카카오 계정 E2E)**: 실제 카카오 발급 토큰으로의 성공 경로는 앱↔카카오 SDK 연동(flutter-dev) 후 수동 검증 필요 — 서버 단독으로는 실 토큰 확보 불가(가짜 토큰 실패 경로까지만 라이브 검증됨).
