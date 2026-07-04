# 52 · 카카오 로그인 실연동 (클라이언트) — Flutter 보고서

> 2026-07-05 · flutter-dev · 네이티브 앱 키 `0cf9d58a088406435bcafe4313e9a7a2`
> (패키지 `com.qkrxodud.runningcrew` + 디버그 키해시로 카카오 콘솔 등록됨)

## 1. 완료 기준 결과

| 게이트 | 결과 |
|---|---|
| `flutter analyze` | **No issues found** (0) |
| `flutter test` | **All tests passed** — 182건 (신규 login_screen 5건 포함) |
| `flutter build apk --debug --dart-define-from-file=config/dev.json` | **성공** — `build/app/outputs/flutter-apk/app-debug.apk` |

(빌드 로그의 `adb: no devices/emulators found` 는 회귀 R-005 의 adb reverse 자동 개통
태스크가 기기 미연결 시 조용히 건너뛰는 정상 동작 — 빌드 자체는 성공.)

## 2. 구현 요약 (3줄)

1. `kakao_flutter_sdk_user`(2.0.0+1) 도입 후 SDK import 를 `platform/auth/kakao_auth_service.dart` 한 곳에 격리 — `KakaoAuthService`(access token 반환·취소는 null·실패는 예외) 인터페이스 + `KakaoSdkAuthService`(톡 로그인→계정 로그인 폴백) + `initKakaoSdk()`(main 부트스트랩, 키 비면 생략).
2. 로그인 화면 카카오 버튼을 `kakaoLoginReadyProvider`(키 존재) 게이트로 활성화 — 취득 토큰을 **접두어 없이** 기존 `authControllerProvider.loginWithKakao` → `POST /auth/login`(`kakao_access_token`)으로 전달(경로·DTO 스텁과 동일, 분기 없음). dev 스텁 섹션은 비prod에서 그대로 유지.
3. AndroidManifest 에 `AuthCodeCustomTabsActivity`(scheme `kakao0cf9…`, host `oauth`) + `com.kakao.talk` 패키지 가시성 쿼리 추가, config 3종(dev/sandbox/prod) 모두 `KAKAO_APP_KEY` 기입, `AppConfig.kakaoLoginReady` 게이트 추가.

### 사용한 계약
- `docs/contracts/auth-api.md §1` — `POST /api/v1/auth/login` `{ kakao_access_token, client_meta }` → JWT 쌍. **클라는 스텁/실검증을 분기하지 않는다**(서버 verifier 소관). §4 스텁 규약도 무변경.

### 변경 파일
- 신규: `app/lib/platform/auth/kakao_auth_service.dart`, `app/test/features/auth/login_screen_test.dart`
- 수정: `app/lib/main.dart`(initKakaoSdk), `app/lib/app/app_config.dart`(kakaoLoginReady), `app/lib/app/providers.dart`(kakaoAuthServiceProvider·kakaoLoginReadyProvider), `app/lib/app/auth_controller.dart`(loginWithKakao / `_completeLogin` 공통화), `app/lib/features/auth/login_screen.dart`(버튼 활성·`_loginKakao`·`_KakaoLoginButton`)
- 설정: `app/android/app/src/main/AndroidManifest.xml`, `app/config/{dev,sandbox,prod}.json`, `app/pubspec.yaml`/`pubspec.lock`

## 3. 수동 검증 절차 (실 카카오 E2E — 자동 불가)

실 로그인은 **실기기 + 카카오 콘솔에 등록된 팀원 계정**이 필요하다. 콘솔 앱은
개발 상태이므로 "팀원(테스터)"으로 등록된 카카오 계정만 로그인 가능하다.

전제: 서버는 **실 카카오 verifier 프로필**로 기동(스텁 프로필 아님 — `RealKakaoTokenVerifier` 경로).
`app/config/dev.json` 이미 키 기입됨. 실기기 USB 연결 후:

1. `cd app && flutter run --dart-define-from-file=config/dev.json` (adb reverse 는 gradle 태스크가 자동 개통 — R-005).
2. 로그인 화면에 초록(lime) **"카카오로 시작하기"** 버튼이 활성 표시되는지 확인(비활성 "준비 중"이면 키 미주입).
3. **카카오톡 설치 기기**: 버튼 탭 → 카카오톡 앱으로 전환 → 동의 → 앱 복귀. **미설치/톡 미로그인 기기**: 카카오계정 웹(CustomTabs) 로그인 → redirect(`kakao0cf9…://oauth`)로 앱 복귀.
4. 서버 로그에 `POST /api/v1/auth/login` 200 + JWT 발급 확인. 신규 계정이면 온보딩(닉네임) 화면으로, 기존이면 홈으로 라우팅되는지 확인.
5. **취소 경로**: 동의 화면에서 뒤로가기 → 오류 문구 없이 로그인 화면 유지(조용히 종결).
6. **거부 경로**(선택): 콘솔 미등록 계정으로 시도 시 서버 401 `AUTH_KAKAO_TOKEN_INVALID` → "카카오 인증에 실패했습니다" 표시 확인.

자동 위젯 테스트가 커버하는 것(SDK 없이 페이크로): 게이트 on/off, 토큰이 접두어 없이
서버로 전달, 취소(null) 무오류, 401 오류 문구, SDK 예외 시 일반 오류 문구.

## 4. 보류 / 후속

- **실 E2E는 미검증**(위 수동 절차 필요) — 실기기·등록 계정·실 verifier 서버 기동 3요소 대기.
- **iOS**: Info.plist 의 `CFBundleURLSchemes`(kakao{키}) · `LSApplicationQueriesSchemes`(kakaokompassauth/kakaolink) · `KAKAO_APP_KEY` 미설정. iOS 확장 배치에서 처리(SDK·인터페이스는 양 플랫폼 공용이라 상위 레이어 무수정).
- **카카오 버튼 브랜딩**: 디자인 일관성 위해 앱 lime CTA 로 구현. 카카오 공식 버튼 가이드라인(옐로 #FEE500 + 심볼) 준수는 스토어 출시 전 폴리시 항목.
- **release 서명**: `signingConfig`가 아직 debug 키 — 릴리스 키해시를 콘솔에 별도 등록해야 release APK 로그인 동작(현재 디버그 키해시만 등록됨).
- `appVersion` 상수(`data/auth_repository.dart`)는 여전히 하드코딩 `1.0.0` — 기존 TODO(package_info_plus) 유지, 이번 범위 밖.
