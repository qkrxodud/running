import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';
import '../../core/model/history_dtos.dart';
import '../../core/model/track_dtos.dart';
import '../race/race_format.dart';
import '../race/race_providers.dart';
import '../race/result_format.dart';
import 'history_providers.dart';

/// 내 기록 히스토리·개인 최고기록 화면 (C5) — history-api §1·§2 소비.
///
/// 디자인: 1a 라임 "내 기록/히스토리·개인 최고기록". 완주·DNF 전체 노출,
/// "취소된 세션" 배지, PB 뱃지(완주만). FINISHED 항목엔 "코스로 만들기"(승격,
/// course-api §4) 버튼 노출 → 성공 시 코스 목록 반영.
class HistoryScreen extends ConsumerWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('내 기록'),
          bottom: const TabBar(
            labelColor: AppColors.ink,
            indicatorColor: AppColors.ink,
            tabs: [Tab(text: '기록'), Tab(text: '개인 최고')],
          ),
        ),
        body: SafeArea(
          child: TabBarView(
            children: [
              _RecordsTab(),
              _PersonalBestsTab(),
            ],
          ),
        ),
      ),
    );
  }
}

class _RecordsTab extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final records = ref.watch(myRecordsProvider);
    return records.when(
      loading: () =>
          const Center(child: CircularProgressIndicator(color: AppColors.ink)),
      error: (e, _) => _errorView(
          '기록을 불러오지 못했습니다.', () => ref.invalidate(myRecordsProvider)),
      data: (page) {
        if (page.items.isEmpty) {
          return const _EmptyView(
              icon: Icons.directions_run,
              message: '아직 완주한 기록이 없어요.\n레이스를 뛰고 기록을 남겨보세요.');
        }
        return ListView.separated(
          padding: const EdgeInsets.all(20),
          itemCount: page.items.length,
          separatorBuilder: (_, _) => const SizedBox(height: 12),
          itemBuilder: (_, i) => _RecordCard(item: page.items[i]),
        );
      },
    );
  }
}

class _RecordCard extends ConsumerStatefulWidget {
  const _RecordCard({required this.item});
  final RecordHistoryItem item;

  @override
  ConsumerState<_RecordCard> createState() => _RecordCardState();
}

class _RecordCardState extends ConsumerState<_RecordCard> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final finished = item.finishStatus == FinishStatus.finished;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  item.courseName,
                  style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: AppColors.ink),
                ),
              ),
              if (item.sessionCancelled)
                const StatusBadge(label: '취소된 세션', color: AppColors.muted)
              else if (!finished)
                const StatusBadge(label: '미완주', color: AppColors.mutedAlt)
              else if (item.isPb)
                const StatusBadge(label: '🏅 PB', color: AppColors.lime),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            RaceFormat.dateTime(item.scheduledAt),
            style: const TextStyle(color: AppColors.muted, fontSize: 12),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              _stat('기록', ResultFormat.time(item.recordTimeS)),
              const SizedBox(width: 20),
              _stat('페이스', ResultFormat.pace(item.avgPaceSPerKm)),
              const SizedBox(width: 20),
              _stat('거리', ResultFormat.distance(item.totalDistanceM)),
              if (finished && item.rank != null) ...[
                const SizedBox(width: 20),
                _stat('순위', '${item.rank}위'),
              ],
            ],
          ),
          // 승격 버튼 — 본인 FINISHED 트랙만(취소 세션이어도 완주면 가능 — PR-2/4).
          if (item.canPromote) ...[
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _busy ? null : () => _promote(item),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.ink,
                side: const BorderSide(color: AppColors.ink),
                minimumSize: const Size.fromHeight(44),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
              ),
              icon: const Icon(Icons.add_road, size: 18),
              label: Text(_busy ? '승격 중…' : '코스로 만들기'),
            ),
          ],
        ],
      ),
    );
  }

  Widget _stat(String label, String value) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: const TextStyle(color: AppColors.muted, fontSize: 11)),
          const SizedBox(height: 2),
          Text(value,
              style: AppTypography.record
                  .copyWith(fontSize: 15, color: AppColors.ink, letterSpacing: 0)),
        ],
      );

  /// 승격 — 이름 입력 후 crewId 해석(세션 상세) → course-api §4 호출.
  Future<void> _promote(RecordHistoryItem item) async {
    final name = await _askCourseName(item.courseName);
    if (name == null || !mounted) return;

    setState(() => _busy = true);
    try {
      // 승격은 crewId 필요(course-api §4) — 세션 상세에서 해석.
      final session =
          await ref.read(sessionRepositoryProvider).detail(item.sessionId);
      final course = await ref.read(courseRepositoryProvider).promote(
            session.crewId,
            sourceTrackRecordId: item.trackRecordId,
            name: name,
          );
      // 성공 → 해당 크루 코스 목록 반영.
      ref.invalidate(crewCoursesProvider(session.crewId));
      if (mounted) {
        _snack('"${course.name}" 코스를 만들었어요.');
      }
    } on ApiException catch (e) {
      if (mounted) _snack(_promoteError(e));
    } on Object {
      if (mounted) _snack('서버에 연결할 수 없습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _promoteError(ApiException e) => switch (e.code) {
        // 코드로만 분기(메시지 매칭 금지). 상세 사유는 서버 message.
        'COURSE_PROMOTION_INELIGIBLE' =>
          '코스로 만들 수 없는 기록이에요: ${e.message}',
        'FORBIDDEN' => '본인 기록만 코스로 만들 수 있어요.',
        'CREW_CLOSED' => '종료된 크루에는 코스를 추가할 수 없어요.',
        'NOT_FOUND' => '기록 또는 크루를 찾을 수 없어요.',
        _ => '코스 만들기에 실패했어요: ${e.message}',
      };

  Future<String?> _askCourseName(String defaultName) {
    final controller = TextEditingController(text: defaultName);
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('코스로 만들기'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('이 완주 기록을 크루 코스로 등록해요.\n거리·경로는 서버가 확정합니다.',
                style: TextStyle(fontSize: 13, color: AppColors.muted)),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              autofocus: true,
              maxLength: 50,
              decoration: const InputDecoration(hintText: '코스 이름 (1~50자)'),
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.of(ctx).pop(), child: const Text('취소')),
          FilledButton(
            onPressed: () {
              final v = controller.text.trim();
              if (v.isEmpty) return;
              Navigator.of(ctx).pop(v);
            },
            child: const Text('만들기'),
          ),
        ],
      ),
    );
  }

  void _snack(String msg) => ScaffoldMessenger.of(context)
      .showSnackBar(SnackBar(content: Text(msg)));
}

class _PersonalBestsTab extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pbs = ref.watch(myPersonalBestsProvider);
    return pbs.when(
      loading: () =>
          const Center(child: CircularProgressIndicator(color: AppColors.ink)),
      error: (e, _) => _errorView(
          'PB를 불러오지 못했습니다.', () => ref.invalidate(myPersonalBestsProvider)),
      data: (page) {
        if (page.items.isEmpty) {
          return const _EmptyView(
              icon: Icons.emoji_events_outlined,
              message: '아직 개인 최고 기록이 없어요.\n코스를 완주하면 PB가 쌓여요.');
        }
        return ListView.separated(
          padding: const EdgeInsets.all(20),
          itemCount: page.items.length,
          separatorBuilder: (_, _) => const SizedBox(height: 12),
          itemBuilder: (_, i) => _PbCard(item: page.items[i]),
        );
      },
    );
  }
}

class _PbCard extends StatelessWidget {
  const _PbCard({required this.item});
  final PersonalBestItem item;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.ink,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.courseName,
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 15,
                        fontWeight: FontWeight.w800)),
                const SizedBox(height: 2),
                Text(
                  '${RaceFormat.distance(item.distanceM)} · ${ResultFormat.pace(item.avgPaceSPerKm)}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              ],
            ),
          ),
          Text(
            ResultFormat.time(item.bestRecordTimeS),
            style: AppTypography.record.copyWith(
                fontSize: 22, color: AppColors.lime, letterSpacing: 0),
          ),
        ],
      ),
    );
  }
}

class _EmptyView extends StatelessWidget {
  const _EmptyView({required this.icon, required this.message});
  final IconData icon;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 48, color: AppColors.muted),
          const SizedBox(height: 12),
          Text(message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted)),
        ],
      ),
    );
  }
}

Widget _errorView(String msg, VoidCallback onRetry) => Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(msg, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    );
