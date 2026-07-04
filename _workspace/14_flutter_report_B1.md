# 14 — Flutter 개발자 보고 (배치 B1: 인증·User·Crew 클라 소비 개통)

> 작성: flutter-dev · 2026-07-04 · 대상: B1-C1~C5 (`app/`)
> 기준: `11_planner_plan_B.md`(B1), `12_analyst_design_B.md`(§2 401 분기·§6 enum 폴백), `docs/contracts/`(auth·user·crew v0.2·conventions v0.1.1·app-version), flutter-client·domain-model 스킬, 디자인 `1a 라임`
> 선행: `04_flutter_report.md`(배치 A). 중단된 세션(모델·데이터 골격 존재)을 인벤토리 후 이어서 완수.

## 0. 완료 기준 결과

| 항목 | 결과 |
|---|---|
| `flutter analyze` | **No issues found** (0) |
| `flutter test` | **All tests passed** (92개: 기존 61 + 신규 31) |
| `flutter build apk --debug` | **성공** — `app-debug.apk`, 스파이크 `/spike` 보존 확인 |
| R-002 코어 순수성 (allowlist 가드) | **green** — dio/secure_storage/riverpod 전부 `lib/data`·`lib/app` 밖, `lib/core` 0건 |

## 1. 중단 시점 인벤토리 (재작성 안 함 — 검증 후 승계)

이전 세션이 완성해 둔 것(검토 결과 계약 정합, 그대로 채택):
- `lib/core/model/` 7파일: enum_codec(폴백 유틸)·api_error·page_response + auth/user/crew/app_version DTO. 모든 enum `unknown` 폴백 + `toWire()` 송신 폴백 금지.
- `lib/data/` 6파일: api_client(dio + `AuthInterceptor`)·token_store(secure/inmemory)·auth/user/crew/app_version repository.
- `lib/app/`: auth_controller(상태머신)·providers(composition root)·app_theme(1a 라임 토큰).
- `lib/features/auth/login_screen.dart`(dev 스텁 로그인).
- `lib/core/version/force_update_policy.dart`(순수 semver).

## 2. 이번 세션 구현 (개통·화면·라우팅)

### 화면 (features/)
- **onboarding/onboarding_screen** — 닉네임 설정(PUT nickname). `onboarding_completed` 게이트.
- **crew/crew_home_screen** — 홈=내 크루 목록(GET /crews). 카드·역할 배지·빈 상태·FAB(만들기)·헤더 참가/설정. 디자인 Screen 1 토큰.
- **crew/crew_create_screen** — 크루 생성(POST /crews), name 1~50자 검증.
- **crew/crew_join_screen** — 초대코드 참가(POST /crews/join). 6자 대문자 정규화·허용문자 formatter·계약 오류 code별 문구.
- **crew/crew_detail_screen** — 상세(GET /crews/{id}). 멤버 목록=**합류 인앱 갈음(O-1)**. 크루장 한정 초대코드 생성(POST invite-codes)+클립보드 복사. 디자인 Screen 6 토큰(잉크 카드·라임 코드·Space Grotesk).
- **settings/settings_screen** — 닉네임 수정·로그아웃·**회원 탈퇴 2단 확인**(재로그인=신규계정·복구불가 고지, user-api §3). 약관/방침 placeholder 상수·dev 스파이크 진입(debug).
- **update/force_update_screen** + **splash/splash_screen** — 강제 업데이트 차단·부트스트랩 대기.

### 라우팅·부트스트랩
- `app/router.dart` **provider 화** — `AuthStatus`(unknown/loggedOut/needsOnboarding/authenticated) + 강제 업데이트에 반응하는 redirect. `/spike`는 게이트 우회(실기기 검증 보존). refreshListenable로 리버포드→go_router 브리지.
- `main.dart` — 기동 시 `bootstrap()`(토큰→me 복원), `routerProvider` 소비.
- 공용 위젯 `features/common/crew_avatar` — userId 결정론 색 배정(accent 팔레트).
- 정리: 구 `features/home/home_screen`은 CrewHomeScreen로 대체(파일 삭제는 샌드박스 제약으로 보류 — deprecated 라이브러리로 비움, 참조 0).

### dio 배선·401 (B1-C1)
- `AuthInterceptor`(QueuedInterceptor): `AUTH_TOKEN_EXPIRED`만 refresh **1회**(`auth_retried` 플래그로 무한 루프 차단) 후 원요청 재시도. `UNAUTHORIZED`/`AUTH_REFRESH_INVALID` 또는 재시도 재실패 → 토큰 폐기·`onSessionExpired`. refresh는 인터셉터 미부착 bare dio(재귀 방지).

## 3. 사용한 계약

- auth-api v0.1 — login(스텁 `stub:{id}`)·refresh 쌍회전·§3 401 code 분기.
- user-api v0.1 — me·nickname(온보딩 겸용)·withdraw(204)·device-token(서버만, 클라 M3 대기).
- crew-api v0.2 — 크루 CRUD·초대코드 생성/참가·오류코드 전수(INVITE_CODE_*·ALREADY_JOINED·CREW_CLOSED·FORBIDDEN).
- app-version v0.1 — 강제 업데이트(404·오류=통과, 가용성 우선).
- conventions v0.1.1 — snake_case·`{code,message}`·offset 페이지네이션·UTC.

## 4. test-engineer 이관 (순수 함수 — 골든/경계 확장 대상)

배치 A 이관(5종) 유지 + B1 신규:
| 대상 | 위치 | 현재 테스트 |
|---|---|---|
| `ForceUpdatePolicy.isUpdateRequired` | core/version/force_update_policy.dart | semver·가용성 우선(파싱불가=false) — `test/core/version/` |
| `InviteCodeFormat.isValid/normalize` | core/model/crew_dtos.dart | 참가 화면 검증 경유(직접 유닛 추가 여지) |
| enum `parse`/`wireValues` (5종) | core/model/{user,crew}_dtos.dart | **계약 값집합 대조**(§6.3) — `test/core/model/enum_contract_test.dart` |
| `validateNickname` | data/user_repository.dart | 온보딩 화면 경유(1~30자·제어문자) |
- 경계면 회귀 대상 추천: enum 계약 대조 테스트는 서버 enum 추가 시 3자 대조를 강제하므로 **회귀 레지스트리 R-001 재발 방지 장치**로 지정 권장.

## 5. qa 경계면 (계약↔서버↔클라 3자 대조 대상)

1. **app-version 3자 대조**(QA 3차 이월) — 클라 DTO(`AppVersionInfo`)·강제업데이트 로직 개통. 서버 구현과 대조 가능.
2. **401 분기 정합** — 클라는 code로만 분기(`AUTH_TOKEN_EXPIRED`→갱신 1회, 그 외→재로그인). 서버가 만료/위조/WITHDRAWN을 이 3 code로 정확히 분리 반환하는지 대조 필요. 무한 루프 없음은 `test/data/auth_interceptor_test.dart`로 박제(5케이스).
3. **enum 값 집합** — user.status/crew.status/crew_member.role/status/platform 클라 집합 == 계약. 서버 응답 실측과 대조.
4. **crew 오류 code 소비** — 참가 실패 문구가 계약 8종 code에 매핑(message 매칭 금지 준수).
5. **탈퇴 계약** — 클라는 DELETE 204 후 로컬 토큰 폐기·loggedOut 전이. 서버 토큰 무효화(이후 401 UNAUTHORIZED)와의 상호작용 확인.
6. **R-002 코어 순수성** — allowlist 가드 green 재확인(dio 유입 없음).

## 6. 보류·대기 (발급물 게이트)

- **카카오 로그인 실연동** — 앱 키 대기. login_screen에 스텁 폼(dev, prod 미포함) + 카카오 버튼 자리(disabled) 고정.
- **초대 카톡 공유·딥링크** — 도메인/카카오 대기. 코드 표시+클립보드 복사까지 구현, 카톡 버튼은 disabled placeholder(교체 지점 주석).
- **방침·약관 URL** — 도메인 대기. `kPrivacyPolicyUrl`/`kTermsOfServiceUrl` placeholder 상수 격리, url_launcher 미배선(탭 시 준비중 스낵바).
- **강제 업데이트 스토어 딥링크** — 배포 채널 확정 후. 차단 화면 버튼 disabled.
- **디바이스 토큰(FCM) 클라 취득** — Firebase 대기(M3). 서버 API만 존재.
- **폰트(Pretendard/Space Grotesk)** — 자산 미확보로 시스템 폰트 대체. `AppTypography`에 tabular figures로 숫자 정렬만 보장, 자산 확보 시 fontFamily 단일 지정으로 반영.
- **package_info_plus** — `appVersion` 하드코딩 `1.0.0`(pubspec와 수동 동기). 강제업데이트·client_meta에 사용, M0 이후 빌드 산출물 직독으로 승격.

## 7. 자동 검증 불가 (수동 절차 유지)

- 백그라운드 트래킹 실기기 1시간 유실 테스트 — 배치 A 절차 그대로(스파이크 `/spike` 보존). 설정 화면 dev 진입점(debug)으로도 도달.
- 실기기 인증 왕복(스텁 로그인→온보딩→크루 생성/참가)은 로컬 서버(B1 백엔드) 기동 후 수동 확인 권장 — `--dart-define=API_BASE_URL=…`, dev 로그인 `stub:{id}`.
