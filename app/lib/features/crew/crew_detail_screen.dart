import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';
import '../../core/model/crew_dtos.dart';
import '../../data/crew_repository.dart' show inviteCodeDefaultMaxUses, inviteCodeDefaultExpiresHours;
import '../common/crew_avatar.dart';
import 'crew_providers.dart';

/// 크루 상세 (crew-api.md §3) — 멤버 목록이 합류 알림의 인앱 갈음 지점(O-1).
/// 크루장은 초대 코드 생성 가능(§4). 카톡 공유·딥링크는 대기(도메인/카카오).
class CrewDetailScreen extends ConsumerWidget {
  const CrewDetailScreen({super.key, required this.crewId});

  final int crewId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(crewDetailProvider(crewId));
    return Scaffold(
      appBar: AppBar(title: const Text('크루')),
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: detail.when(
          loading: () =>
              const Center(child: CircularProgressIndicator(color: AppColors.ink)),
          error: (e, _) => _error(context, ref, e),
          data: (crew) => _CrewDetailBody(crew: crew),
        ),
      ),
    );
  }

  Widget _error(BuildContext context, WidgetRef ref, Object e) {
    final msg = e is ApiException && e.code == 'FORBIDDEN'
        ? '이 크루의 멤버만 볼 수 있습니다.'
        : e is ApiException && e.code == 'NOT_FOUND'
            ? '존재하지 않는 크루입니다.'
            : '크루 정보를 불러오지 못했습니다.';
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(msg, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () => ref.invalidate(crewDetailProvider(crewId)),
            child: const Text('다시 시도'),
          ),
        ],
      ),
    );
  }
}

class _CrewDetailBody extends ConsumerStatefulWidget {
  const _CrewDetailBody({required this.crew});

  final CrewDetail crew;

  @override
  ConsumerState<_CrewDetailBody> createState() => _CrewDetailBodyState();
}

class _CrewDetailBodyState extends ConsumerState<_CrewDetailBody> {
  InviteCodeInfo? _invite;
  bool _generating = false;

  bool get _isLeader =>
      widget.crew.leader.userId ==
      ref.read(authControllerProvider).profile?.id;

  bool get _isClosed => widget.crew.status == CrewStatus.closed;

  Future<void> _generateCode() async {
    setState(() => _generating = true);
    try {
      final info = await ref.read(crewRepositoryProvider).createInviteCode(
            widget.crew.id,
            maxUses: inviteCodeDefaultMaxUses,
            expiresInHours: inviteCodeDefaultExpiresHours,
          );
      if (mounted) setState(() => _invite = info);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('초대 코드 생성 실패: ${e.message}')),
        );
      }
    } finally {
      if (mounted) setState(() => _generating = false);
    }
  }

  Future<void> _copyCode() async {
    final code = _invite?.code;
    if (code == null) return;
    await Clipboard.setData(ClipboardData(text: code));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('초대 코드를 복사했습니다.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final crew = widget.crew;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
      children: [
        // 크루 정체성
        Column(
          children: [
            Container(
              width: 78,
              height: 78,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: AppColors.ink,
                borderRadius: BorderRadius.circular(24),
              ),
              child: const Icon(Icons.groups, size: 38, color: AppColors.lime),
            ),
            const SizedBox(height: 12),
            Text(
              crew.name,
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 4),
            Text(
              '멤버 ${crew.members.length}명 · ${crew.leader.nickname} 크루장'
              '${_isClosed ? ' · 종료됨' : ''}',
              style: const TextStyle(fontSize: 13, color: AppColors.muted),
            ),
          ],
        ),
        const SizedBox(height: 22),

        // 레이스 세션 진입 (session-api §2) — 목록/생성은 세션 화면에서.
        _RaceSessionsEntry(crewId: crew.id),
        const SizedBox(height: 24),

        // 초대 코드 (크루장 전용, ACTIVE 크루만)
        if (_isLeader && !_isClosed) _inviteSection(),

        // 멤버 목록 (합류 인앱 갈음)
        const Padding(
          padding: EdgeInsets.only(bottom: 12, top: 4),
          child: Text(
            '멤버',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              letterSpacing: 1,
              color: Color(0xFF9AA18C),
            ),
          ),
        ),
        ...crew.members.map((m) => _MemberRow(member: m)),
      ],
    );
  }

  Widget _inviteSection() {
    final invite = _invite;
    return Container(
      margin: const EdgeInsets.only(bottom: 24),
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: AppColors.ink,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        children: [
          const Text(
            '초대 코드',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              letterSpacing: 1,
              color: Color(0xFF9AA18C),
            ),
          ),
          const SizedBox(height: 12),
          if (invite == null) ...[
            const Text(
              '코드를 생성해 크루원을 초대하세요.',
              style: TextStyle(color: Color(0xFF7E8676), fontSize: 13),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _generating ? null : _generateCode,
              child: Text(_generating ? '생성 중…' : '초대 코드 만들기'),
            ),
          ] else ...[
            Text(
              invite.code,
              style: const TextStyle(
                color: AppColors.lime,
                fontSize: 40,
                fontWeight: FontWeight.w700,
                letterSpacing: 6,
                fontFeatures: [FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '최대 ${invite.maxUses}회 · 만료 ${_formatExpiry(invite.expiresAt)}',
              style: const TextStyle(color: Color(0xFF7E8676), fontSize: 12),
            ),
            const SizedBox(height: 18),
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _copyCode,
                    icon: const Icon(Icons.content_copy, size: 20),
                    label: const Text('코드 복사'),
                  ),
                ),
                const SizedBox(width: 10),
                // 카카오톡 공유·딥링크는 대기(도메인/카카오 발급물) — 교체 지점.
                Tooltip(
                  message: '카카오톡 공유는 준비 중입니다 (키 발급 대기).',
                  child: Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      color: const Color(0xFF23271D),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Icon(Icons.chat_bubble_outline,
                        color: AppColors.muted),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  /// 만료까지 남은 시간 표기 (KST 등 변환은 클라 소관 — 여기선 상대 시간).
  String _formatExpiry(DateTime expiresAtUtc) {
    final remaining = expiresAtUtc.difference(DateTime.now().toUtc());
    if (remaining.isNegative) return '만료됨';
    final hours = remaining.inHours;
    if (hours >= 24) return '${(hours / 24).floor()}일 후';
    if (hours >= 1) return '$hours시간 후';
    return '${remaining.inMinutes}분 후';
  }
}

/// 크루 상세 → 레이스 세션 목록 진입 카드.
class _RaceSessionsEntry extends StatelessWidget {
  const _RaceSessionsEntry({required this.crewId});

  final int crewId;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.lime,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: () => context.push('/crews/$crewId/sessions'),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(
            children: [
              const Icon(Icons.flag, color: AppColors.ink),
              const SizedBox(width: 12),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('레이스 세션',
                        style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                            color: AppColors.ink)),
                    Text('예정된 레이스 보기 · 참가 신청',
                        style: TextStyle(fontSize: 13, color: Color(0xFF3C4232))),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: AppColors.ink),
            ],
          ),
        ),
      ),
    );
  }
}

class _MemberRow extends StatelessWidget {
  const _MemberRow({required this.member});

  final CrewMemberView member;

  @override
  Widget build(BuildContext context) {
    final isLeader = member.role == CrewRole.leader;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          CrewAvatar(userId: member.userId, nickname: member.nickname),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  member.nickname,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: AppColors.ink,
                  ),
                ),
                if (member.joinedAt != null)
                  Text(
                    _joinedLabel(member.joinedAt!),
                    style: const TextStyle(fontSize: 12, color: AppColors.muted),
                  ),
              ],
            ),
          ),
          if (isLeader)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
              decoration: BoxDecoration(
                color: AppColors.ink,
                borderRadius: BorderRadius.circular(100),
              ),
              child: const Text(
                '크루장',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: AppColors.lime,
                ),
              ),
            ),
        ],
      ),
    );
  }

  String _joinedLabel(DateTime joinedAtUtc) {
    final local = joinedAtUtc.toLocal();
    return '${local.month}월 ${local.day}일 합류';
  }
}
