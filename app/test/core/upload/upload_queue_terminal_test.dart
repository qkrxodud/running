import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/upload/backoff_policy.dart';
import 'package:running/core/upload/upload_queue.dart';

/// markTerminalFailure — 비재시도 종단 실패도 데이터를 버리지 않는다(C-5/R-002).
void main() {
  UploadQueue queued() =>
      UploadQueue()..enqueue(const UploadTask(id: 't1', recordRef: 'rec-1'));

  test('markTerminalFailure: 백오프 없이 즉시 failed, due 아님(재시도 안 함)', () {
    final q = queued();
    q.markInFlight('t1');
    q.markTerminalFailure('t1');

    final t = q.tasks.single;
    expect(t.status, UploadStatus.failed);
    expect(t.nextAttemptAt, isNull);
    // 종단이라 미래 어느 시점에도 재시도 대상 아님.
    expect(q.dueTasks(DateTime(2999)), isEmpty);
  });

  test('종단 실패는 큐에서 제거되지 않는다(로컬 보존 — purgeSucceeded 무영향)', () {
    final q = queued();
    q.markTerminalFailure('t1');
    q.purgeSucceeded();
    expect(q.tasks, hasLength(1), reason: 'succeeded 아닌 failed 는 보존');
  });

  test('재시도 소진(markFailed)과 동일한 종단 상태로 수렴', () {
    final q = queued();
    // maxAttempts=1 이면 첫 실패에서 바로 failed.
    q.markInFlight('t1');
    q.markFailed('t1', DateTime(2026), const BackoffPolicy(maxAttempts: 1));
    expect(q.tasks.single.status, UploadStatus.failed);
    expect(q.dueTasks(DateTime(2999)), isEmpty);
  });
}
