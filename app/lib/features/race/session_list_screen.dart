import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/crew_dtos.dart';
import '../../core/model/race_dtos.dart';
import '../crew/crew_providers.dart';
import 'race_format.dart';
import 'race_providers.dart';

/// 세션 목록 (session-api.md §2) — 크루 상세에서 진입. 상태 뱃지 렌더.
/// 크루장이면 "세션 만들기" FAB 노출(crewDetail 로 leader 판정).
class SessionListScreen extends ConsumerWidget {
  const SessionListScreen({super.key, required this.crewId});

  final int crewId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sessions = ref.watch(crewSessionsProvider(crewId));
    final crew = ref.watch(crewDetailProvider(crewId));
    final myId = ref.watch(authControllerProvider).profile?.id;
    final isLeader = crew.asData?.value.leader.userId == myId;
    final isClosed = crew.asData?.value.status == CrewStatus.closed;

    return Scaffold(
      appBar: AppBar(title: const Text('레이스 세션')),
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async =>
              ref.refresh(crewSessionsProvider(crewId).future),
          child: sessions.when(
            loading: () => const Center(
                child: CircularProgressIndicator(color: AppColors.ink)),
            error: (e, _) => _ErrorRetry(
              onRetry: () => ref.invalidate(crewSessionsProvider(crewId)),
            ),
            data: (page) {
              if (page.items.isEmpty) {
                return const _EmptySessions();
              }
              return ListView.separated(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 96),
                itemCount: page.items.length,
                separatorBuilder: (_, _) => const SizedBox(height: 12),
                itemBuilder: (context, i) =>
                    _SessionCard(session: page.items[i]),
              );
            },
          ),
        ),
      ),
      floatingActionButton: (isLeader && !isClosed)
          ? FloatingActionButton.extended(
              onPressed: () =>
                  context.push('/crews/$crewId/sessions/create'),
              backgroundColor: AppColors.lime,
              foregroundColor: AppColors.ink,
              icon: const Icon(Icons.add),
              label: const Text('세션 만들기',
                  style: TextStyle(fontWeight: FontWeight.w700)),
            )
          : null,
    );
  }
}

class _SessionCard extends StatelessWidget {
  const _SessionCard({required this.session});

  final SessionSummary session;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: () => context.push('/sessions/${session.id}'),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      session.courseName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                        color: AppColors.ink,
                      ),
                    ),
                  ),
                  StatusBadge(
                    label: RaceFormat.sessionStatusLabel(session.status),
                    color: RaceFormat.sessionStatusColor(session.status),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  const Icon(Icons.schedule, size: 15, color: AppColors.muted),
                  const SizedBox(width: 4),
                  Text(
                    RaceFormat.dateTime(session.scheduledAt),
                    style:
                        const TextStyle(fontSize: 13, color: AppColors.muted),
                  ),
                  const SizedBox(width: 12),
                  const Icon(Icons.group, size: 15, color: AppColors.muted),
                  const SizedBox(width: 4),
                  Text(
                    '${session.participantCount}명',
                    style:
                        const TextStyle(fontSize: 13, color: AppColors.muted),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptySessions extends StatelessWidget {
  const _EmptySessions();

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: const [
        SizedBox(height: 120),
        Icon(Icons.flag_outlined, size: 64, color: AppColors.muted),
        SizedBox(height: 16),
        Center(
          child: Text(
            '아직 예정된 레이스가 없어요',
            style: TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w800,
              color: AppColors.ink,
            ),
          ),
        ),
        SizedBox(height: 8),
        Center(
          child: Text(
            '크루장이 코스를 골라 세션을 만들 수 있어요.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.muted),
          ),
        ),
      ],
    );
  }
}

class _ErrorRetry extends StatelessWidget {
  const _ErrorRetry({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        const SizedBox(height: 160),
        const Center(
          child: Text('세션 목록을 불러오지 못했습니다.',
              style: TextStyle(color: AppColors.muted)),
        ),
        const SizedBox(height: 12),
        Center(
          child: TextButton(onPressed: onRetry, child: const Text('다시 시도')),
        ),
      ],
    );
  }
}
