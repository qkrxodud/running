import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/api_error.dart';
import 'package:running/core/model/track_dtos.dart';
import 'package:running/core/model/track_error.dart';
import 'package:running/core/upload/backoff_policy.dart';
import 'package:running/core/upload/upload_queue.dart';
import 'package:running/data/track_repository.dart';
import 'package:running/data/upload_coordinator.dart';

/// UploadCoordinator — 순수 큐를 HTTP 리포지토리로 배선.
/// 성공 확인 후에만 로컬 삭제(R-002), 재시도 시 멱등 키 재사용, 4xx 종단.
void main() {
  TrackUploadJob jobFor(String ref, String uploadId) => TrackUploadJob(
        sessionId: 91,
        request: TrackUploadRequest(
          clientUploadId: uploadId,
          startedAt: DateTime.utc(2026, 7, 10, 21),
          polyline: '_p~iF',
          timestamps: const [1752181205000],
          speeds: const [0.0],
          accuracies: const [12.0],
        ),
      );

  TrackRecordResponse okResponse() => TrackRecordResponse.fromJson({
        'track_record_id': 1,
        'session_id': 91,
        'user_id': 7,
        'processing_status': 'PROCESSED',
        'finish_status': 'FINISHED',
        'started_at': '2026-07-10T21:00:05Z',
        'finished_at': '2026-07-10T21:26:41Z',
        'total_distance_m': 5000,
        'total_time_s': 1596,
        'gps_gap_count': 0,
      });

  test('성공: markSucceeded 확정 후에만 로컬 삭제 훅 호출(R-002 순서)', () async {
    final events = <String>[];
    final repo = _FakeRepo(onUpload: (s, r) async {
      events.add('upload');
      return okResponse();
    });
    final queue = UploadQueue();
    final coord = UploadCoordinator(
      repository: repo,
      queue: queue,
      loadJob: (ref) async => jobFor(ref, 'id-1'),
      onSucceeded: (ref, resp) async => events.add('delete-local:$ref'),
    );
    coord.enqueue('rec-1');

    final ok = await coord.pump(DateTime(2026, 7, 10));

    expect(ok, ['rec-1']);
    // 업로드가 로컬 삭제보다 먼저 — 성공 확인 전 삭제 없음.
    expect(events, ['upload', 'delete-local:rec-1']);
    expect(queue.tasks, isEmpty, reason: 'succeeded 는 purge 됨');
  });

  test('네트워크 실패: markFailed(백오프) — 재시도 대기, 로컬 보존, 삭제 없음', () async {
    final deleted = <String>[];
    final repo = _FakeRepo(onUpload: (s, r) async {
      throw const ApiException(
          statusCode: 0, code: 'NETWORK_ERROR', message: '');
    });
    final queue = UploadQueue();
    final failures = <TrackUploadErrorKind>[];
    final coord = UploadCoordinator(
      repository: repo,
      queue: queue,
      backoff: const BackoffPolicy(baseDelay: Duration(seconds: 2)),
      loadJob: (ref) async => jobFor(ref, 'id-1'),
      onSucceeded: (ref, resp) async => deleted.add(ref),
      onFailed: (ref, kind) => failures.add(kind),
    );
    coord.enqueue('rec-1');

    final now = DateTime(2026, 7, 10);
    await coord.pump(now);

    expect(failures, [TrackUploadErrorKind.network]);
    expect(deleted, isEmpty, reason: '실패 시 로컬 삭제 절대 금지');
    final t = queue.tasks.single;
    expect(t.status, UploadStatus.pending, reason: '재시도 대기');
    expect(t.nextAttemptAt!.isAfter(now), isTrue);
    // 백오프 시각 전엔 due 아님.
    expect(queue.dueTasks(now), isEmpty);
  });

  test('403 비멤버: markTerminalFailure — 재시도 안 함, 로컬 보존', () async {
    final repo = _FakeRepo(onUpload: (s, r) async {
      throw const ApiException(
          statusCode: 403, code: 'FORBIDDEN', message: '멤버 아님');
    });
    final queue = UploadQueue();
    final failures = <TrackUploadErrorKind>[];
    final coord = UploadCoordinator(
      repository: repo,
      queue: queue,
      loadJob: (ref) async => jobFor(ref, 'id-1'),
      onFailed: (ref, kind) => failures.add(kind),
    );
    coord.enqueue('rec-1');

    await coord.pump(DateTime(2026, 7, 10));

    expect(failures, [TrackUploadErrorKind.forbiddenNotMember]);
    expect(queue.tasks.single.status, UploadStatus.failed);
    expect(queue.dueTasks(DateTime(2999)), isEmpty, reason: '종단 — 재시도 없음');
    queue.purgeSucceeded();
    expect(queue.tasks, hasLength(1), reason: '데이터 보존');
  });

  test('멱등: 재시도 시 동일 client_upload_id 재사용(재생성 없음)', () async {
    final usedIds = <String>[];
    var attempt = 0;
    final repo = _FakeRepo(onUpload: (s, r) async {
      usedIds.add(r.clientUploadId);
      attempt++;
      if (attempt == 1) {
        throw const ApiException(
            statusCode: 0, code: 'NETWORK_ERROR', message: '');
      }
      return okResponse();
    });
    final queue = UploadQueue();
    // loadJob 은 recordRef 로 저장된 요청을 돌려주므로 매번 같은 id.
    final coord = UploadCoordinator(
      repository: repo,
      queue: queue,
      backoff: const BackoffPolicy(baseDelay: Duration(seconds: 1)),
      loadJob: (ref) async => jobFor(ref, 'id-STABLE'),
    );
    coord.enqueue('rec-1');

    final t0 = DateTime(2026, 7, 10, 0, 0, 0);
    await coord.pump(t0); // 실패 → 백오프
    final t1 = t0.add(const Duration(seconds: 5)); // 백오프 경과
    final ok = await coord.pump(t1); // 재시도 → 성공

    expect(usedIds, ['id-STABLE', 'id-STABLE'],
        reason: '재시도는 새 키를 만들지 않고 동일 멱등 키 재사용');
    expect(ok, ['rec-1']);
  });
}

class _FakeRepo implements TrackRepository {
  _FakeRepo({required this.onUpload});
  final Future<TrackRecordResponse> Function(int s, TrackUploadRequest r)
      onUpload;

  @override
  Future<TrackRecordResponse> upload(
          int sessionId, TrackUploadRequest request) =>
      onUpload(sessionId, request);

  @override
  Future<TrackRecordResponse?> myTrack(int sessionId) async => null;

  @override
  Future<ResultQueryOutcome> result(int sessionId) async =>
      const ResultPending();
}
