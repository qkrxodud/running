/// 강제 업데이트 판단 — 순수 Dart (B1-C3, 레지스트리 이월 항목).
///
/// 원칙: **가용성 우선** — 홈서버 다운·응답 파싱 불가·서버 미설정(404)이
/// 앱 사용을 막으면 안 된다. 판단 불가능한 모든 경우는 "업데이트 불요"다.
class ForceUpdatePolicy {
  const ForceUpdatePolicy._();

  /// semver 비교: [current] < [minimum] 이면 true (강제 업데이트 필요).
  ///
  /// - 파싱 실패(비정상 문자열)는 false — 가용성 우선.
  /// - 누락 세그먼트는 0 취급 ("1.2" == "1.2.0").
  static bool isUpdateRequired({
    required String currentVersion,
    required String minVersion,
  }) {
    final current = _parse(currentVersion);
    final minimum = _parse(minVersion);
    if (current == null || minimum == null) return false;
    return _compare(current, minimum) < 0;
  }

  /// "1.2.3" / "1.2.3+45"(빌드 넘버 무시) → [1,2,3]. 실패 시 null.
  static List<int>? _parse(String version) {
    final core = version.split('+').first.trim();
    if (core.isEmpty) return null;
    final parts = core.split('.');
    if (parts.length > 3) return null;
    final nums = <int>[];
    for (final p in parts) {
      final n = int.tryParse(p);
      if (n == null || n < 0) return null;
      nums.add(n);
    }
    while (nums.length < 3) {
      nums.add(0);
    }
    return nums;
  }

  static int _compare(List<int> a, List<int> b) {
    for (var i = 0; i < 3; i++) {
      final d = a[i].compareTo(b[i]);
      if (d != 0) return d;
    }
    return 0;
  }
}
