import 'dart:convert';
import 'dart:io';

/// 앱↔서버 공유 계약 픽스처 로더(P26-2/C8).
///
/// `docs/contracts/fixtures/<name>` 은 **서버 통합 테스트가 실 DTO 직렬화로 생성**해 커밋한
/// 단일 바이트 파일이다(서버 `SharedContractFixtureTest`). 앱 테스트가 **같은 파일**을 파싱함으로써
/// 계약 drift(필드 개명·키 생략 변화·avg_pace 필드명)가 생기면 서버/앱 중 하나가 red 가 된다.
///
/// `flutter test` 의 cwd 는 패키지 루트(app/)이므로 `../docs/...` 가 기본. 저장소 루트에서 실행되는
/// 경우를 대비해 후보 경로를 순차 시도한다.
Map<String, dynamic> loadContractFixture(String name) {
  const candidates = [
    'docs/contracts/fixtures/',        // cwd = 저장소 루트
    '../docs/contracts/fixtures/',     // cwd = app/ (flutter test 기본)
    '../../docs/contracts/fixtures/',  // 방어적 여유
  ];
  for (final base in candidates) {
    final file = File('$base$name');
    if (file.existsSync()) {
      return jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
    }
  }
  throw StateError(
    '공유 계약 픽스처를 찾지 못함: $name '
    '(서버 SharedContractFixtureTest 로 생성됐는지 확인). 시도 경로: $candidates',
  );
}
