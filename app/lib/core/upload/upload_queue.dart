import 'backoff_policy.dart';

/// 업로드 작업의 상태(클라 로컬 관점).
enum UploadStatus { pending, inFlight, succeeded, failed }

/// 큐의 단일 업로드 작업(불변). 실제 페이로드는 참조([recordRef])만 들고,
/// 블롭은 로컬 저장 모듈에 있다 — 큐는 "무엇을/언제 재시도할지"만 안다.
class UploadTask {
  const UploadTask({
    required this.id,
    required this.recordRef,
    this.status = UploadStatus.pending,
    this.attempts = 0,
    this.nextAttemptAt,
  });

  /// 큐 내 고유 id.
  final String id;

  /// 로컬 저장소의 트랙 레코드 식별자(파일명/세션키 등). 큐는 참조만 보유.
  final String recordRef;

  final UploadStatus status;

  /// 지금까지 시도 횟수.
  final int attempts;

  /// 이 시각 이후 재시도 가능(백오프 결과). null 이면 즉시 가능.
  final DateTime? nextAttemptAt;

  UploadTask copyWith({
    UploadStatus? status,
    int? attempts,
    DateTime? nextAttemptAt,
    bool clearNextAttempt = false,
  }) {
    return UploadTask(
      id: id,
      recordRef: recordRef,
      status: status ?? this.status,
      attempts: attempts ?? this.attempts,
      nextAttemptAt:
          clearNextAttempt ? null : (nextAttemptAt ?? this.nextAttemptAt),
    );
  }
}

/// 업로드 재시도 큐 — 순수 상태 전이 로직(HTTP·IO·시계 없음, now 주입).
///
/// 실제 네트워크 전송은 배치 B(dio)에서 배선한다. 이 클래스는 재시도 스케줄과
/// 상태 전이만 순수하게 관리해 테스트 가능하게 둔다.
///
/// **로컬 우선 불변식(C-5):** 성공([markSucceeded]) 확인 전에는 어떤 전이도
/// 작업을 큐에서 제거하지 않는다. 실패는 재시도로 남고, maxAttempts 초과여도
/// [UploadStatus.failed] 로 보존될 뿐 데이터를 버리지 않는다 — 로컬 원본 삭제는
/// 오직 succeeded 확정 이후 소비자의 별도 결정이다.
class UploadQueue {
  UploadQueue([List<UploadTask>? initial])
      : _tasks = [...?initial];

  final List<UploadTask> _tasks;

  List<UploadTask> get tasks => List.unmodifiable(_tasks);

  UploadTask? _find(String id) {
    for (final t in _tasks) {
      if (t.id == id) return t;
    }
    return null;
  }

  int _indexOf(String id) => _tasks.indexWhere((t) => t.id == id);

  /// 작업 추가(중복 id 는 무시). 앱 재시작 시 저장된 큐를 initial 로 복원한다.
  void enqueue(UploadTask task) {
    if (_find(task.id) != null) return;
    _tasks.add(task);
  }

  /// 지금([now]) 시도 가능한 작업들: pending 이며 nextAttemptAt 도달.
  ///
  /// 재시도 대기 중인 실패는 [markFailed] 에서 pending 으로 되돌아가 여기 포함된다.
  /// [UploadStatus.failed] 는 maxAttempts 소진된 **종단** 상태라 due 아니다(보존만).
  List<UploadTask> dueTasks(DateTime now) {
    return [
      for (final t in _tasks)
        if (t.status == UploadStatus.pending &&
            (t.nextAttemptAt == null || !t.nextAttemptAt!.isAfter(now)))
          t,
    ];
  }

  /// 전송 시작 표시(attempts +1, inFlight).
  void markInFlight(String id) {
    final i = _indexOf(id);
    if (i < 0) return;
    _tasks[i] = _tasks[i].copyWith(
      status: UploadStatus.inFlight,
      attempts: _tasks[i].attempts + 1,
      clearNextAttempt: true,
    );
  }

  /// 실패 처리: 백오프로 다음 시도 시각 계산. maxAttempts 초과면 failed 로
  /// 남겨 보존(제거하지 않음). 그 외엔 pending 으로 되돌려 재시도 대기.
  void markFailed(String id, DateTime now, BackoffPolicy policy) {
    final i = _indexOf(id);
    if (i < 0) return;
    final task = _tasks[i];
    if (!policy.shouldRetry(task.attempts)) {
      _tasks[i] = task.copyWith(status: UploadStatus.failed);
      return;
    }
    final delay = policy.delayForAttempt(task.attempts);
    _tasks[i] = task.copyWith(
      status: UploadStatus.pending,
      nextAttemptAt: now.add(delay),
    );
  }

  /// 비재시도 종단 실패: 재시도해도 결과가 바뀌지 않는 오류(4xx 클라 오류 —
  /// 비멤버 403·미등록 409·payload 400 등)를 백오프 재시도 없이 즉시 [failed]
  /// 로 종단한다. maxAttempts 소진과 동일하게 **보존만**(제거하지 않음 — C-5/R-002).
  /// 로컬 원본 삭제는 여전히 succeeded 확정 이후에만 가능하다(여기선 삭제 없음).
  void markTerminalFailure(String id) {
    final i = _indexOf(id);
    if (i < 0) return;
    _tasks[i] = _tasks[i].copyWith(
      status: UploadStatus.failed,
      clearNextAttempt: true,
    );
  }

  /// 성공 확정: succeeded 표시. 이 시점 이후에만 소비자가 로컬 원본 삭제를
  /// 결정할 수 있다(그 전에 삭제하는 코드를 만들지 않는다 — C-5).
  void markSucceeded(String id) {
    final i = _indexOf(id);
    if (i < 0) return;
    _tasks[i] = _tasks[i].copyWith(
      status: UploadStatus.succeeded,
      clearNextAttempt: true,
    );
  }

  /// succeeded 작업만 큐에서 제거(로컬 원본 삭제 후 호출). 실패/대기는 유지.
  void purgeSucceeded() {
    _tasks.removeWhere((t) => t.status == UploadStatus.succeeded);
  }
}
