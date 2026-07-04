import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/upload/backoff_policy.dart';
import 'package:running/core/upload/upload_queue.dart';

void main() {
  const policy = BackoffPolicy(
    baseDelay: Duration(seconds: 2),
    multiplier: 2.0,
    maxAttempts: 3,
  );
  final t0 = DateTime.utc(2026, 7, 4, 8);

  UploadQueue queueWith(String id) =>
      UploadQueue()..enqueue(UploadTask(id: id, recordRef: 'rec_$id'));

  test('enqueue 는 중복 id 를 무시한다', () {
    final q = queueWith('a')..enqueue(const UploadTask(id: 'a', recordRef: 'x'));
    expect(q.tasks.length, 1);
  });

  test('신규 작업은 즉시 due', () {
    final q = queueWith('a');
    expect(q.dueTasks(t0).map((t) => t.id), ['a']);
  });

  test('실패 시 백오프로 nextAttemptAt 이 미뤄지고 그 전까진 due 아님', () {
    final q = queueWith('a');
    q.markInFlight('a'); // attempts 1
    q.markFailed('a', t0, policy); // delay = 2s (attempt 1)
    expect(q.dueTasks(t0.add(const Duration(seconds: 1))), isEmpty);
    expect(q.dueTasks(t0.add(const Duration(seconds: 2))).map((t) => t.id), ['a']);
  });

  test('maxAttempts 초과여도 작업을 버리지 않고 failed 로 보존한다 (C-5)', () {
    final q = queueWith('a');
    for (var i = 0; i < 3; i++) {
      q.markInFlight('a');
      q.markFailed('a', t0, policy);
    }
    // attempts=3 == maxAttempts → shouldRetry false → failed 로 남음
    expect(q.tasks.single.status, UploadStatus.failed);
    expect(q.tasks.length, 1); // 제거되지 않음
    expect(q.dueTasks(t0.add(const Duration(hours: 1))), isEmpty);
  });

  test('성공 확정 후에만 purge 로 제거된다', () {
    final q = queueWith('a');
    q.markInFlight('a');
    q.markSucceeded('a');
    expect(q.tasks.single.status, UploadStatus.succeeded);
    q.purgeSucceeded();
    expect(q.tasks, isEmpty);
  });

  test('purgeSucceeded 는 미완료 작업을 남긴다', () {
    final q = queueWith('a')..enqueue(const UploadTask(id: 'b', recordRef: 'rb'));
    q.markInFlight('a');
    q.markSucceeded('a');
    q.purgeSucceeded();
    expect(q.tasks.map((t) => t.id), ['b']);
  });

  group('UploadQueue 전이 경계·로컬 우선(C-5)', () {
    test('존재하지 않는 id 에 대한 전이는 무해(no-op)하고 큐를 바꾸지 않는다', () {
      final q = queueWith('a');
      q.markInFlight('missing');
      q.markSucceeded('missing');
      q.markFailed('missing', t0, policy);
      expect(q.tasks.single.id, 'a');
      expect(q.tasks.single.status, UploadStatus.pending);
      expect(q.tasks.single.attempts, 0);
    });

    test('pending 만 due — inFlight/succeeded/failed 는 due 아님', () {
      final q = queueWith('a');
      q.markInFlight('a');
      expect(q.dueTasks(t0.add(const Duration(hours: 1))), isEmpty); // inFlight
      q.markSucceeded('a');
      expect(q.dueTasks(t0.add(const Duration(hours: 1))), isEmpty); // succeeded
    });

    test('markInFlight 는 호출마다 attempts 를 1씩 증가시킨다', () {
      final q = queueWith('a');
      q.markInFlight('a');
      expect(q.tasks.single.attempts, 1);
      q.markFailed('a', t0, policy); // pending 복귀
      q.markInFlight('a');
      expect(q.tasks.single.attempts, 2);
    });

    test('실패→pending 재시도 후 다시 due 로 재진입한다', () {
      final q = queueWith('a');
      q.markInFlight('a');
      q.markFailed('a', t0, policy); // attempt 1 → delay 2s
      expect(q.tasks.single.status, UploadStatus.pending);
      final due = q.dueTasks(t0.add(const Duration(seconds: 2)));
      expect(due.map((t) => t.id), ['a']);
    });

    test('maxAttempts 소진 failed 는 데이터(recordRef)를 보존한다 — 삭제 아님', () {
      final q = queueWith('a');
      for (var i = 0; i < 3; i++) {
        q.markInFlight('a');
        q.markFailed('a', t0, policy);
      }
      expect(q.tasks.single.status, UploadStatus.failed);
      expect(q.tasks.single.recordRef, 'rec_a'); // 참조 보존
      expect(q.tasks.single.attempts, 3);
    });

    test('purgeSucceeded 는 succeeded 만 제거 — 성공 전 제거 경로 부재', () {
      // failed(소진) + pending + inFlight 는 purge 후에도 전부 남는다.
      final q = queueWith('failedOne')
        ..enqueue(UploadTask(id: 'pendingOne', recordRef: 'rec_pendingOne'))
        ..enqueue(UploadTask(id: 'flightOne', recordRef: 'rec_flightOne'));
      // failedOne → maxAttempts 소진
      for (var i = 0; i < 3; i++) {
        q.markInFlight('failedOne');
        q.markFailed('failedOne', t0, policy);
      }
      q.markInFlight('flightOne');
      q.purgeSucceeded();
      // 성공 확정이 하나도 없으므로 아무것도 제거되지 않는다.
      expect(
        q.tasks.map((t) => '${t.id}:${t.status.name}').toSet(),
        {
          'failedOne:failed',
          'pendingOne:pending',
          'flightOne:inFlight',
        },
      );
    });

    test('nextAttemptAt 가 정확히 now 인 시점은 due (경계 포함)', () {
      final q = queueWith('a');
      q.markInFlight('a');
      q.markFailed('a', t0, policy); // nextAttemptAt = t0 + 2s
      expect(q.dueTasks(t0.add(const Duration(seconds: 2))).map((t) => t.id),
          ['a']);
    });
  });
}
