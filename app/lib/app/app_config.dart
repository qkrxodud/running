import 'package:flutter/foundation.dart';

import 'env_gate.dart';

/// dev/sandbox/prod 환경 분리 (B2-C4) — dart-define 기반 주입 단일 창구.
///
/// 값은 빌드시 `--dart-define-from-file=config/{dev,sandbox,prod}.json` 로 주입한다
/// (개별 `--dart-define=KEY=VALUE` 도 동일 키로 동작). 운영 DB 를 개발 트래픽이
/// 오염하지 않도록, base URL·키·지도 Client ID 를 환경별로 갈라 넣는 구조다.
///
/// **환경 3종:** dev(localhost, adb reverse 전제) · sandbox(맥/홈서버 LAN IP,
/// 카카오 키 전까지 크루원 테스트용) · prod(도메인 https). 게이트 판단은
/// [EnvGate] 순수 함수에 위임하며, 규칙은 "prod 여부" 기준이다 —
/// dev 도구·스텁 로그인은 `environment != 'prod'` 일 때 열린다(sandbox 포함).
///
/// **주입 자리(placeholder)** — 실제 키 값은 M0 발급물 대기(§계획 §5):
/// - [apiBaseUrl]      : 서버 base URL (dev = 에뮬레이터→호스트).
/// - [kakaoAppKey]     : 카카오 로그인 (앱 키 대기). 확보 시 값만 주입.
/// - [naverMapClientId]: 네이버 지도 Client ID (대기). 확보 시 값만 주입 →
///   `CoursePolylineMap` 어댑터를 placeholder 에서 실 SDK 로 교체.
///
/// 어느 코드도 `String.fromEnvironment` 를 직접 부르지 않고 이 클래스만 본다 —
/// 주입 지점이 흩어지지 않게 단일화(리뷰·교체 용이).
class AppConfig {
  const AppConfig._();

  /// 환경 식별자 — `dev`(기본) | `sandbox` | `prod`. 게이트 판단 기준.
  static const String environment =
      String.fromEnvironment('APP_ENV', defaultValue: 'dev');

  /// 서버 base URL. dev 기본 = localhost:8080 — 실기기·에뮬레이터 공통으로
  /// `adb reverse tcp:8080 tcp:8080` 터널을 전제로 한다 (10.0.2.2는 에뮬레이터
  /// 전용이라 실기기에서 NETWORK_ERROR — 이 전환의 이유).
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  /// 카카오 앱 키 — **대기**(M0). 빈 값이면 실 카카오 로그인 미배선(스텁 유지).
  static const String kakaoAppKey =
      String.fromEnvironment('KAKAO_APP_KEY', defaultValue: '');

  /// 네이버 지도 Client ID — **대기**(M0). 빈 값이면 지도 placeholder 유지.
  /// 교체 지점: `features/race/map/course_polyline_map.dart` 팩토리 주석 참조.
  static const String naverMapClientId =
      String.fromEnvironment('NAVER_MAP_CLIENT_ID', defaultValue: '');

  /// 스텁 로그인 노출 플래그. dev/sandbox config = true, prod config = false.
  /// 미주입 시 디버그 실행이면 true(개발 편의). prod 에서는 이 값과 무관하게
  /// [devLoginEnabled] 가 항상 차단한다(안전장치).
  static const bool _devLoginFlag =
      bool.fromEnvironment('DEV_LOGIN', defaultValue: kDebugMode);

  static bool get isProd => EnvGate.isProd(environment);
  static bool get isSandbox => EnvGate.isSandbox(environment);
  static bool get isDev => EnvGate.isDev(environment);

  /// dev 도구(스텁 로그인·트래킹 스파이크 `/spike`) 노출 여부.
  /// prod 가 아니면 열림(dev·sandbox 공통) — 단, 디버그 실행은 항상 허용(편의).
  static bool get showDevTools =>
      EnvGate.showDevTools(environment, debug: kDebugMode);

  /// 스텁(개발용) 로그인 노출 여부 — 로그인 화면이 이 값만 본다(단일 창구).
  /// prod 차단, dev·sandbox 는 DEV_LOGIN 플래그(둘 다 기본 true)를 따른다.
  static bool get devLoginEnabled =>
      EnvGate.devLoginEnabled(environment, devLoginFlag: _devLoginFlag);

  /// 지도 실 SDK 사용 가능 여부(Client ID 확보 시 true). 현재는 항상 false →
  /// placeholder 렌더. 교체 시 이 게이트가 어댑터 선택을 가른다.
  static bool get naverMapReady => naverMapClientId.isNotEmpty;
}
