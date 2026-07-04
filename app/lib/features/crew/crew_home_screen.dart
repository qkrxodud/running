import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';
import '../../core/model/crew_dtos.dart';
import 'crew_providers.dart';

/// 홈 = 내 크루 목록 (crew-api.md §2). 디자인 1a 라임 Screen 1(크루 홈) 토큰 준수.
/// 예정 레이스 자리는 B2(Race) 대기 — 뼈대만 표기.
class CrewHomeScreen extends ConsumerWidget {
  const CrewHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final crews = ref.watch(myCrewsProvider);
    return Scaffold(
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async => ref.refresh(myCrewsProvider.future),
          child: CustomScrollView(
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 4),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'MY CREW',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              letterSpacing: 1.3,
                              color: Color(0xFF9AA18C),
                            ),
                          ),
                          SizedBox(height: 2),
                          Text(
                            '러닝크루',
                            style: TextStyle(
                              fontSize: 22,
                              fontWeight: FontWeight.w800,
                              color: AppColors.ink,
                            ),
                          ),
                        ],
                      ),
                      Row(
                        children: [
                          IconButton(
                            tooltip: '초대 코드로 참가',
                            onPressed: () => context.push('/crews/join'),
                            icon: const Icon(Icons.vpn_key_outlined,
                                color: AppColors.ink),
                          ),
                          IconButton(
                            tooltip: '설정',
                            onPressed: () => context.push('/settings'),
                            icon:
                                const Icon(Icons.settings, color: AppColors.ink),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              _body(context, ref, crews),
            ],
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/crews/create'),
        backgroundColor: AppColors.lime,
        foregroundColor: AppColors.ink,
        icon: const Icon(Icons.add),
        label: const Text('크루 만들기',
            style: TextStyle(fontWeight: FontWeight.w700)),
      ),
    );
  }

  Widget _body(BuildContext context, WidgetRef ref,
      AsyncValue<dynamic> crews) {
    return crews.when(
      loading: () => const SliverFillRemaining(
        child: Center(child: CircularProgressIndicator(color: AppColors.ink)),
      ),
      error: (e, _) => SliverFillRemaining(
        child: _ErrorRetry(
          message: '크루 목록을 불러오지 못했습니다.',
          onRetry: () => ref.invalidate(myCrewsProvider),
        ),
      ),
      data: (page) {
        final items = (page.items as List).cast<CrewSummary>();
        if (items.isEmpty) return const SliverFillRemaining(child: _EmptyCrews());
        return SliverPadding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 96),
          sliver: SliverList.separated(
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(height: 12),
            itemBuilder: (context, i) => _CrewCard(crew: items[i]),
          ),
        );
      },
    );
  }
}

class _CrewCard extends StatelessWidget {
  const _CrewCard({required this.crew});

  final CrewSummary crew;

  @override
  Widget build(BuildContext context) {
    final closed = crew.status == CrewStatus.closed;
    final isLeader = crew.role == CrewRole.leader;
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: () => context.push('/crews/${crew.id}'),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(
            children: [
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: AppColors.ink,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(Icons.groups, color: AppColors.lime),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            crew.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w800,
                              color: AppColors.ink,
                            ),
                          ),
                        ),
                        if (isLeader) ...[
                          const SizedBox(width: 8),
                          const _Badge(text: '크루장'),
                        ],
                        if (closed) ...[
                          const SizedBox(width: 8),
                          const _Badge(text: '종료', muted: true),
                        ],
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '멤버 ${crew.memberCount}명',
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.muted,
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: AppColors.muted),
            ],
          ),
        ),
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.text, this.muted = false});

  final String text;
  final bool muted;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(
        color: muted ? AppColors.bgAlt : AppColors.ink,
        borderRadius: BorderRadius.circular(100),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: muted ? AppColors.muted : AppColors.lime,
        ),
      ),
    );
  }
}

class _EmptyCrews extends StatelessWidget {
  const _EmptyCrews();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.groups_outlined, size: 64, color: AppColors.muted),
            const SizedBox(height: 16),
            const Text(
              '아직 소속된 크루가 없어요',
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w800,
                color: AppColors.ink,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              '크루를 만들거나 초대 코드로 참가하세요.',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.muted),
            ),
            const SizedBox(height: 24),
            OutlinedButton.icon(
              onPressed: () => context.push('/crews/join'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.ink,
                side: const BorderSide(color: AppColors.ink),
                minimumSize: const Size.fromHeight(52),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                ),
              ),
              icon: const Icon(Icons.vpn_key),
              label: const Text('초대 코드로 참가'),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorRetry extends StatelessWidget {
  const _ErrorRetry({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(message, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(onPressed: onRetry, child: const Text('다시 시도')),
        ],
      ),
    );
  }
}
