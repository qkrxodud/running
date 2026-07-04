import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/env_gate.dart';

/// 환경 게이트(dev/sandbox/prod × 스텁로그인·/spike) 회귀 방지.
///
/// 핵심 회귀: `env == 'dev'` 로 좁게 비교하면 sandbox 가 dev 도구·스텁 로그인에서
/// 차단된다. 게이트는 `env != 'prod'` 로 열려야 한다. sandbox 케이스가 이 테스트의
/// 존재 이유다.
void main() {
  group('EnvGate.isXxx — 3환경 식별', () {
    test('정확한 문자열만 참', () {
      expect(EnvGate.isDev('dev'), isTrue);
      expect(EnvGate.isSandbox('sandbox'), isTrue);
      expect(EnvGate.isProd('prod'), isTrue);

      expect(EnvGate.isDev('sandbox'), isFalse);
      expect(EnvGate.isProd('sandbox'), isFalse);
      expect(EnvGate.isProd('dev'), isFalse);
    });
  });

  group('showDevTools — /spike 게이트', () {
    test('release 빌드: dev·sandbox 허용, prod 차단', () {
      expect(EnvGate.showDevTools('dev', debug: false), isTrue);
      expect(EnvGate.showDevTools('sandbox', debug: false), isTrue,
          reason: 'sandbox 는 prod 가 아니므로 /spike 열려야 함(회귀 방지)');
      expect(EnvGate.showDevTools('prod', debug: false), isFalse);
    });

    test('debug 빌드: 세 환경 모두 허용(prod 도 편의상)', () {
      expect(EnvGate.showDevTools('dev', debug: true), isTrue);
      expect(EnvGate.showDevTools('sandbox', debug: true), isTrue);
      expect(EnvGate.showDevTools('prod', debug: true), isTrue);
    });
  });

  group('devLoginEnabled — 스텁 로그인 게이트', () {
    test('플래그 true: dev·sandbox 허용, prod 는 차단(안전장치)', () {
      expect(EnvGate.devLoginEnabled('dev', devLoginFlag: true), isTrue);
      expect(EnvGate.devLoginEnabled('sandbox', devLoginFlag: true), isTrue,
          reason: 'sandbox 는 카카오 키 전까지 스텁 로그인으로 크루원 테스트');
      expect(EnvGate.devLoginEnabled('prod', devLoginFlag: true), isFalse,
          reason: 'prod 에 DEV_LOGIN=true 가 실수로 주입돼도 막혀야 함');
    });

    test('플래그 false: 모든 환경에서 차단', () {
      expect(EnvGate.devLoginEnabled('dev', devLoginFlag: false), isFalse);
      expect(EnvGate.devLoginEnabled('sandbox', devLoginFlag: false), isFalse);
      expect(EnvGate.devLoginEnabled('prod', devLoginFlag: false), isFalse);
    });
  });
}
