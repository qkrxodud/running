/// 계약 enum 파싱 공용 유틸 — R-001 유형(미지 enum 값 크래시) 방지 규약의 구현.
///
/// 규칙(설계 12 §6):
/// 1. 서버 enum 을 파싱하는 모든 DTO 는 이 유틸 경유 — enum 마다 `unknown` 멤버.
/// 2. 미지값 수신: 크래시 금지, unknown 폴백 + 로깅([EnumParseLog] 훅).
/// 3. 값 집합의 진실은 `docs/contracts/` — enum 마다 계약 대조 테스트 의무.
/// 4. 송신은 폴백 금지 — unknown 직렬화는 [ContractEnumSendError].
library;

/// 미지값 관측 훅. 기본은 수집만 없음(no-op) — Crashlytics 배선(M3) 시 승격.
class EnumParseLog {
  EnumParseLog._();

  /// 테스트·부트스트랩에서 주입 가능한 관측 콜백.
  static void Function(String context, String rawValue)? onUnknown;

  static void report(String context, String rawValue) {
    onUnknown?.call(context, rawValue);
  }
}

/// 계약 enum 파싱: [wire] 값을 [values] 매핑에서 찾고, 없으면 [unknown] 폴백.
///
/// [context] 는 로깅용 식별자(예: 'crew.status').
T parseContractEnum<T>({
  required String? wire,
  required Map<String, T> values,
  required T unknown,
  required String context,
}) {
  if (wire == null) {
    EnumParseLog.report(context, '<null>');
    return unknown;
  }
  final parsed = values[wire];
  if (parsed == null) {
    EnumParseLog.report(context, wire);
    return unknown;
  }
  return parsed;
}

/// 송신 시 unknown 직렬화 시도 — 파싱 전용 안전장치를 송신에 쓰면 버그다.
class ContractEnumSendError extends Error {
  ContractEnumSendError(this.context);
  final String context;

  @override
  String toString() =>
      'ContractEnumSendError: $context 의 unknown 값은 서버로 직렬화할 수 없다 '
      '(설계 12 §6.4 — 송신 폴백 금지)';
}
