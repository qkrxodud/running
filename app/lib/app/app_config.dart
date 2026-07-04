import 'package:flutter/foundation.dart';

/// dev/prod 환경 분리 (B2-C4) — dart-define 기반 주입 단일 창구.
///
/// 값은 빌드시 `--dart-define-from-file=config/{dev,prod}.json` 로 주입한다
/// (개별 `--dart-define=KEY=VALUE` 도 동일 키로 동작). 운영 DB 를 개발 트래픽이
/// 오염하지 않도록, base URL·키·지도 Client ID 를 환경별로 갈라 넣는 구조다.
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

  /// 환경 식별자 — `dev`(기본) | `prod`. redirect·dev 도구 게이트 기준.
  static const String environment =
      String.fromEnvironment('APP_ENV', defaultValue: 'dev');

  /// 서버 base URL. dev 기본 = Android 에뮬레이터에서 호스트 localhost.
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  /// 카카오 앱 키 — **대기**(M0). 빈 값이면 실 카카오 로그인 미배선(스텁 유지).
  static const String kakaoAppKey =
      String.fromEnvironment('KAKAO_APP_KEY', defaultValue: '');

  /// 네이버 지도 Client ID — **대기**(M0). 빈 값이면 지도 placeholder 유지.
  /// 교체 지점: `features/race/map/course_polyline_map.dart` 팩토리 주석 참조.
  static const String naverMapClientId =
      String.fromEnvironment('NAVER_MAP_CLIENT_ID', defaultValue: '');

  static bool get isProd => environment == 'prod';
  static bool get isDev => !isProd;

  /// dev 도구(스텁 로그인·트래킹 스파이크) 노출 여부.
  /// prod 빌드에서는 숨김 — 단, 디버그 실행은 dev 로 간주(개발 편의).
  static bool get showDevTools => isDev || kDebugMode;

  /// 지도 실 SDK 사용 가능 여부(Client ID 확보 시 true). 현재는 항상 false →
  /// placeholder 렌더. 교체 시 이 게이트가 어댑터 선택을 가른다.
  static bool get naverMapReady => naverMapClientId.isNotEmpty;
}
