import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/model/crew_dtos.dart';
import '../../core/model/page_response.dart';

/// 내 크루 목록 (GET /crews). autoDispose — 화면 이탈 시 정리, 재진입 시 새로고침.
final myCrewsProvider =
    FutureProvider.autoDispose<PageResponse<CrewSummary>>((ref) {
  return ref.watch(crewRepositoryProvider).myCrews();
});

/// 크루 상세 (GET /crews/{id}). 생성·참가·초대코드 발급 후 invalidate 로 갱신.
final crewDetailProvider =
    FutureProvider.autoDispose.family<CrewDetail, int>((ref, crewId) {
  return ref.watch(crewRepositoryProvider).detail(crewId);
});
