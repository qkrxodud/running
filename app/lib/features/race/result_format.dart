import '../../core/model/track_dtos.dart';

/// 결과·기록 표시 포맷터(정적·순수) — 기록 숫자는 Space Grotesk(AppTypography.record).
class ResultFormat {
  const ResultFormat._();

  /// 초 → "MM:SS" 또는 "H:MM:SS". null/음수 → "--:--"(P46-1 안전 렌더).
  static String time(int? seconds) {
    if (seconds == null || seconds < 0) return '--:--';
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    final s = seconds % 60;
    final mm = m.toString().padLeft(2, '0');
    final ss = s.toString().padLeft(2, '0');
    return h > 0 ? '$h:$mm:$ss' : '$mm:$ss';
  }

  /// 초/km → "M:SS/km". null → "-"(완주자만 존재 — DNF/DNS 는 null).
  static String pace(int? secPerKm) {
    if (secPerKm == null || secPerKm < 0) return '-';
    final m = secPerKm ~/ 60;
    final s = secPerKm % 60;
    return "$m:${s.toString().padLeft(2, '0')}/km";
  }

  /// 순위 표기 — 서버 rank 그대로(동률 공동순위 1,1,3 은 서버 산정값 반영).
  /// DNF/DNS 는 rank null → "-".
  static String rank(int? r) => r?.toString() ?? '-';

  /// 결과 항목 상태 라벨.
  static String entryStatus(ResultEntryStatus s) => switch (s) {
        ResultEntryStatus.finished => '완주',
        ResultEntryStatus.dnf => '미완주',
        ResultEntryStatus.dns => '불참',
        ResultEntryStatus.unknown => '-',
      };

  /// 거리 표기 — 정제 후 거리(m) → "5.0km". null(DNS) → "-".
  static String distance(int? meters) =>
      meters == null ? '-' : '${(meters / 1000).toStringAsFixed(1)}km';
}

// 주의(R-009): 대기 화면 n/m 진행 카운트(WaitingProgress)는 **제거됐다**.
// 서버가 participation 을 세션 확정 시 일괄 전이하므로(all-or-nothing) 대기 구간엔
// FINISHED/DNF 가 증명적으로 항상 0 → "0/m" 오도. 계약에 per-participant 업로드
// 플래그가 없는 한 진행률 파생값을 UI 가 지어내지 않는다("계약에 없는 파생값을
// UI 에 만들지 않는다" — qa 인계). 진짜 진행률이 필요하면 계약 확장(domain-analyst).
