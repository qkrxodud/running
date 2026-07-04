import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// 불변식 C-4 강제: `lib/core/` 는 순수 Dart — 플랫폼/서드파티 패키지 import 0건.
///
/// **allowlist 방식** (회귀 R-002): denylist 는 목록 밖 패키지(dio,
/// flutter_riverpod 등)가 코어로 유입돼도 통과시키므로, 허용 목록 기반으로
/// 뒤집는다. `lib/core/` 에서 허용되는 import 는:
///   - `dart:*` SDK (core/convert/io/async/math 등 — dart:io 는 저장 모듈의
///     파일 IO 라 허용, 플랫폼 채널 아님)
///   - 상대경로 (코어 내부 참조)
/// 그 외 — 모든 `package:` import — 는 위반이다. 코어에 정말 필요한 순수 Dart
/// 패키지가 생기면 이 테스트의 허용 규칙을 명시적 리뷰와 함께 넓힌다.
///
/// 주의: 이 테스트 파일 자체의 flutter_test import 는 무관 — 스캔 대상은
/// `lib/core/` 뿐이다(test/ 는 스캔하지 않는다).
void main() {
  test('lib/core 의 import 는 dart:* 와 상대경로만 허용된다 (allowlist)', () {
    final coreDir = Directory('lib/core');
    expect(coreDir.existsSync(), isTrue, reason: 'lib/core 가 존재해야 한다');

    // import/export 지시문의 URI 추출 (한 줄 지시문 전제 — dart format 기준).
    final directive = RegExp(
      '''^\\s*(?:import|export)\\s+['"]([^'"]+)['"]''',
      multiLine: true,
    );

    final violations = <String>[];
    for (final entity in coreDir.listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('.dart')) continue;
      final content = entity.readAsStringSync();
      for (final match in directive.allMatches(content)) {
        final uri = match.group(1)!;
        final allowed = uri.startsWith('dart:') || // SDK
            !uri.contains(':'); // 상대경로 (스킴 없음 — package:/dart: 아님)
        if (!allowed) {
          violations.add('${entity.path} → $uri');
        }
      }
    }

    expect(
      violations,
      isEmpty,
      reason: 'core 에 비허용 import 발견 (dart:*/상대경로 외 전부 금지):\n'
          '${violations.join('\n')}',
    );
  });
}
