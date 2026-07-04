/// 클라이언트 로컬 레이스 상태머신 — **서버 Participation 상태와 별개**(순수 Dart).
///
/// `READY → RUNNING → FINISHED_LOCAL → UPLOADED`
///
/// - **FINISHED_LOCAL**: 완주했으나 아직 서버에 전송되지 않은 상태다. 서버는 이
///   상태를 모른다("서버 다운 = 결과 지연이지 유실이 아니다"의 근간). 업로드
///   **성공을 확인한 뒤에만** UPLOADED 로 전이한다(그 전엔 로컬 데이터 삭제 금지).
/// - 서버 `ParticipationStatus`(REGISTERED/STARTED/FINISHED/DNF/DNS/WITHDRAWN)와
///   **혼동 금지** — 이건 기기 로컬 상태다(계획서 §5.3, flutter-client 스킬).
///
/// IO·시계·랜덤 없음 — 골든 테스트 대상. 상태 값은 불변(전이는 새 인스턴스 반환).
library;

/// 클라 로컬 레이스 상태(서버 상태와 별개).
enum RaceLocalState {
  ready('READY'),
  running('RUNNING'),
  finishedLocal('FINISHED_LOCAL'),
  uploaded('UPLOADED');

  const RaceLocalState(this.wire);

  /// 로컬 영속(리커버리)용 문자열. 계약 wire 가 아님 — 서버로 전송하지 않는다.
  final String wire;

  static RaceLocalState fromWire(String wire) => values.firstWhere(
        (s) => s.wire == wire,
        orElse: () =>
            throw ArgumentError('알 수 없는 로컬 레이스 상태: $wire'),
      );
}

/// 불허 전이 시도 — 상태머신 불변식 위반은 조용히 넘기지 않고 즉시 드러낸다.
class RaceStateTransitionError extends Error {
  RaceStateTransitionError(this.from, this.attempted);
  final RaceLocalState from;
  final String attempted;

  @override
  String toString() =>
      'RaceStateTransitionError: $from 에서 "$attempted" 전이는 불가하다 '
      '(READY→RUNNING→FINISHED_LOCAL→UPLOADED 만 허용)';
}

/// 단일 세션의 로컬 레이스 상태(불변 값 객체). 전이는 검증 후 새 인스턴스 반환.
class RaceLocalSession {
  const RaceLocalSession({
    required this.sessionId,
    this.state = RaceLocalState.ready,
  });

  /// 서버 세션 id(레이스 세션). 로컬 상태를 이 세션에 결속한다.
  final int sessionId;
  final RaceLocalState state;

  /// 주행 중(트래커 가동).
  bool get isRunning => state == RaceLocalState.running;

  /// 완주 후 업로드 대기 — UI "업로드 대기 중" + 재시도 노출 근거.
  bool get isAwaitingUpload => state == RaceLocalState.finishedLocal;

  /// 업로드 성공 확정(서버 반영 완료).
  bool get isUploaded => state == RaceLocalState.uploaded;

  /// 로컬 트랙 데이터가 아직 필요한 상태(업로드 확정 전) — 삭제 금지 판단용.
  bool get retainsLocalData =>
      state == RaceLocalState.running ||
      state == RaceLocalState.finishedLocal;

  /// READY → RUNNING. '레이스 시작' 시 1회. 이미 시작됐으면(=RUNNING 이상)
  /// 중복 시작으로 거부(불변식: 동일 세션 재시작 불가).
  RaceLocalSession start() {
    if (state != RaceLocalState.ready) {
      throw RaceStateTransitionError(state, 'start');
    }
    return _to(RaceLocalState.running);
  }

  /// RUNNING → FINISHED_LOCAL. 완주(도착 반경 진입 감지 등) 시 로컬 확정.
  /// **서버 미전송 상태** — 여기서 UPLOADED 로 건너뛰지 않는다.
  RaceLocalSession finishLocal() {
    if (state != RaceLocalState.running) {
      throw RaceStateTransitionError(state, 'finishLocal');
    }
    return _to(RaceLocalState.finishedLocal);
  }

  /// FINISHED_LOCAL → UPLOADED. **업로드 성공을 확인한 뒤에만** 호출한다.
  /// RUNNING 에서 직접 호출 불가(완주 로컬 확정 선행 필수).
  RaceLocalSession markUploaded() {
    if (state != RaceLocalState.finishedLocal) {
      throw RaceStateTransitionError(state, 'markUploaded');
    }
    return _to(RaceLocalState.uploaded);
  }

  RaceLocalSession _to(RaceLocalState next) =>
      RaceLocalSession(sessionId: sessionId, state: next);

  Map<String, dynamic> toJson() => {
        'session_id': sessionId,
        'state': state.wire,
      };

  factory RaceLocalSession.fromJson(Map<String, dynamic> json) =>
      RaceLocalSession(
        sessionId: json['session_id'] as int,
        state: RaceLocalState.fromWire(json['state'] as String),
      );

  @override
  bool operator ==(Object other) =>
      other is RaceLocalSession &&
      other.sessionId == sessionId &&
      other.state == state;

  @override
  int get hashCode => Object.hash(sessionId, state);

  @override
  String toString() => 'RaceLocalSession($sessionId, ${state.wire})';
}

/// 중복 시작 방지 판정(단일 활성 레이스 불변식).
///
/// 진행 중(RUNNING)이거나 업로드 대기(FINISHED_LOCAL)인 세션이 이미 있으면
/// 새 레이스 시작을 거부한다 — 동시에 두 레이스를 뛸 수 없고, 미업로드 완주가
/// 남아 있으면 그것부터 마무리해야 한다. [current] 가 null 이거나 UPLOADED 면 허용.
bool canStartNewRace(RaceLocalSession? current) =>
    current == null || current.state == RaceLocalState.uploaded;
