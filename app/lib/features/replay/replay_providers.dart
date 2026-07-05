import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/model/replay_dtos.dart';

/// 세션 리플레이 스냅샷 (GET /sessions/{id}/replay). autoDispose — 재진입/재시도 시
/// 새로고침(invalidate). GENERATING 이면 재조회로 READY 전환 확인.
final replaySnapshotProvider = FutureProvider.autoDispose
    .family<ReplaySnapshotResponse, int>((ref, sessionId) {
  return ref.watch(replayRepositoryProvider).snapshot(sessionId);
});
