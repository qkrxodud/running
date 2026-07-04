import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/model/page_response.dart';
import '../../core/model/race_dtos.dart';

/// 크루 세션 목록 (GET /crews/{id}/sessions). autoDispose — 재진입 시 새로고침.
/// 세션 생성·명령 후 invalidate 로 갱신.
final crewSessionsProvider = FutureProvider.autoDispose
    .family<PageResponse<SessionSummary>, int>((ref, crewId) {
  return ref.watch(sessionRepositoryProvider).listByCrew(crewId);
});

/// 세션 상세 (GET /sessions/{id}). open/register/start/cancel 후 invalidate.
final sessionDetailProvider = FutureProvider.autoDispose
    .family<SessionDetail, int>((ref, sessionId) {
  return ref.watch(sessionRepositoryProvider).detail(sessionId);
});

/// 세션 생성용 코스 목록 (GET /crews/{id}/courses — dev 시드 코스).
final crewCoursesProvider = FutureProvider.autoDispose
    .family<PageResponse<CourseSummary>, int>((ref, crewId) {
  return ref.watch(courseRepositoryProvider).listByCrew(crewId);
});
