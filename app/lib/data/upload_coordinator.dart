import '../core/model/api_error.dart';
import '../core/model/track_dtos.dart';
import '../core/model/track_error.dart';
import '../core/upload/backoff_policy.dart';
import '../core/upload/upload_queue.dart';
import 'track_repository.dart';

/// 한 건의 업로드 작업 재료 — 세션 id + 요청. `recordRef` 로 로컬에서 로드되며
/// **재시도마다 동일 recordRef → 동일 요청(동일 client_upload_id)** 을 돌려줘야
/// 멱등이 성립한다(track-api §4).
class TrackUploadJob {
  const TrackUploadJob({required this.sessionId, required this.request});
  final int sessionId;
  final TrackUploadRequest request;
}

/// recordRef → 업로드 재료 로더. 로컬 저장소에서 영속된 요청을 읽어온다
/// (요청 생성 시 부여한 client_upload_id 를 그대로 보존해 돌려줘야 한다).
typedef TrackJobLoader = Future<TrackUploadJob> Function(String recordRef);

/// 업로드 성공 확정 후 호출 — **로컬 원본 삭제는 여기서만** 가능(성공 확인 후, R-002).
typedef UploadSuccessSink =
    Future<void> Function(String recordRef, TrackRecordResponse response);

/// 업로드 실패 통지 — UI 가 재시도 버튼/사용자 조치 안내를 분기(code 기반).
typedef UploadFailureSink =
    void Function(String recordRef, TrackUploadErrorKind kind);

/// 순수 [UploadQueue]·[BackoffPolicy] 를 dio HTTP([TrackRepository])로 배선한다.
///
/// 로컬 우선 불변식(C-5/R-002):
/// - 코어 큐는 순수 상태 전이만(HTTP·IO 없음) — 여기서만 네트워크에 닿는다.
/// - **업로드 성공([markSucceeded]) 확인 전에는 로컬 원본을 삭제하지 않는다.**
///   삭제 훅([onSucceeded])은 markSucceeded 직후에만 호출된다.
/// - 실패는 재시도(백오프) 또는 비재시도 종단([markTerminalFailure])으로 **보존**되며
///   어떤 경우에도 데이터를 버리지 않는다.
/// - 재시도는 동일 recordRef 로 [loadJob] 을 재호출 → **동일 멱등 키 재사용**.
class UploadCoordinator {
  UploadCoordinator({
    required this.repository,
    required this.queue,
    required this.loadJob,
    this.backoff = const BackoffPolicy(),
    this.onSucceeded,
    this.onFailed,
  });

  final TrackRepository repository;
  final UploadQueue queue;
  final TrackJobLoader loadJob;
  final BackoffPolicy backoff;
  final UploadSuccessSink? onSucceeded;
  final UploadFailureSink? onFailed;

  /// 완주 후 업로드 태스크 등록(멱등). id=recordRef — 중복 enqueue 무시(큐 규약).
  void enqueue(String recordRef) {
    queue.enqueue(UploadTask(id: recordRef, recordRef: recordRef));
  }

  /// [now] 기준 시도 가능한 작업을 1회씩 전송 시도한다. 앱 재시작/주기 실행에서 호출.
  /// 반환: 이번 pump 에서 성공 확정된 recordRef 목록.
  Future<List<String>> pump(DateTime now) async {
    final succeeded = <String>[];
    for (final task in queue.dueTasks(now)) {
      queue.markInFlight(task.id);
      try {
        final job = await loadJob(task.recordRef);
        final response = await repository.upload(job.sessionId, job.request);
        queue.markSucceeded(task.id);
        // 성공 확인 후에만 로컬 삭제 허용(그 전 삭제 코드 없음 — R-002).
        await onSucceeded?.call(task.recordRef, response);
        queue.purgeSucceeded();
        succeeded.add(task.recordRef);
      } on ApiException catch (e) {
        final kind = classifyTrackUploadError(e);
        if (kind.isRetryable) {
          // 네트워크·5xx: 백오프 재시도(로컬 보존).
          queue.markFailed(task.id, now, backoff);
        } else {
          // 4xx 클라 오류(비멤버 403·미등록/상태 409·중복·payload·413):
          // 재시도 무의미 → 종단(로컬은 여전히 보존, 삭제 없음).
          queue.markTerminalFailure(task.id);
        }
        onFailed?.call(task.recordRef, kind);
      }
    }
    return succeeded;
  }
}
