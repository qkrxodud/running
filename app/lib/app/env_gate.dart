/// 환경(dev/sandbox/prod) 게이트 판단 — 순수 Dart(플랫폼 채널 의존 없음).
///
/// [AppConfig] 는 컴파일타임 상수(`String.fromEnvironment`)를 이 함수들에 넘겨
/// 게이트를 계산한다. 판단부를 순수 함수로 분리한 이유: `AppConfig.environment`
/// 는 dart-define 로 빌드시 고정되어 유닛테스트에서 3환경을 바꿔가며 검증할 수
/// 없다 — 여기 함수는 env 를 인자로 받으므로 dev/sandbox/prod 전 케이스를 테스트로
/// 박제할 수 있다(test-engineer 골든 대상).
///
/// **핵심 규칙: dev 도구·스텁 로그인 게이트는 "prod 여부" 기준이다.**
/// `env == 'dev'` 로 좁게 비교하면 sandbox 가 의도치 않게 차단된다(카카오 키 전까지
/// 크루원 테스트를 sandbox 로 돌리는데 스텁 로그인·/spike 가 막히는 회귀). 따라서
/// 허용 판단은 항상 `env != 'prod'` 로 넓게 연다.
class EnvGate {
  const EnvGate._();

  static const String prod = 'prod';
  static const String sandbox = 'sandbox';
  static const String dev = 'dev';

  static bool isProd(String env) => env == prod;
  static bool isSandbox(String env) => env == sandbox;
  static bool isDev(String env) => env == dev;

  /// dev 도구(스텁 로그인 화면·`/spike` 라우트) 노출 여부.
  /// prod 가 아니면 허용(dev·sandbox 공통). 디버그 빌드는 편의상 항상 허용 —
  /// prod 를 release 로만 배포한다는 전제(§README) 하에서 안전하다.
  static bool showDevTools(String env, {required bool debug}) =>
      !isProd(env) || debug;

  /// 스텁(개발용) 로그인 노출 여부. prod 에서는 [devLoginFlag] 값과 무관하게
  /// 항상 차단(안전장치) — 실수로 prod 에 DEV_LOGIN=true 가 주입돼도 막힌다.
  /// dev·sandbox 는 DEV_LOGIN 플래그를 따른다(둘 다 config 기본 true).
  static bool devLoginEnabled(String env, {required bool devLoginFlag}) =>
      !isProd(env) && devLoginFlag;
}
