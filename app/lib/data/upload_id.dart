import 'dart:math';

/// 멱등 업로드 키(uuid v4) 생성 — 한 번의 완주당 **1회** 호출.
///
/// 생성된 값은 [TrackUploadRequest] 에 실려 로컬에 영속되고, 재시도 시엔
/// 재생성하지 않고 **저장된 동일 값을 재사용**한다(track-api §4 멱등 규약).
/// 랜덤(비순수)이라 core 가 아닌 data 계층에 둔다. [random] 주입으로 테스트 결정화.
String generateClientUploadId([Random? random]) {
  final rnd = random ?? Random.secure();
  final bytes = List<int>.generate(16, (_) => rnd.nextInt(256));
  // RFC 4122 v4: version(4) / variant(10xx).
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  String hex(int start, int end) {
    final sb = StringBuffer();
    for (var i = start; i < end; i++) {
      sb.write(bytes[i].toRadixString(16).padLeft(2, '0'));
    }
    return sb.toString();
  }

  return '${hex(0, 4)}-${hex(4, 6)}-${hex(6, 8)}-${hex(8, 10)}-${hex(10, 16)}';
}
