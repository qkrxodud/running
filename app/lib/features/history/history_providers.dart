import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/model/history_dtos.dart';
import '../../core/model/page_response.dart';

/// 내 기록 히스토리 (GET /me/records). autoDispose — 재진입/승격 후 invalidate.
final myRecordsProvider =
    FutureProvider.autoDispose<PageResponse<RecordHistoryItem>>((ref) {
  return ref.watch(historyRepositoryProvider).myRecords();
});

/// 내 코스별 PB (GET /me/personal-bests).
final myPersonalBestsProvider =
    FutureProvider.autoDispose<PageResponse<PersonalBestItem>>((ref) {
  return ref.watch(historyRepositoryProvider).myPersonalBests();
});
