import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';
import '../../core/model/crew_dtos.dart';
import '../../core/model/race_dtos.dart';
import '../crew/crew_providers.dart';
import 'map/course_polyline_map.dart';
import 'race_format.dart';
import 'race_providers.dart';

/// 세션 상세 (session-api.md §3). 코스 미리보기·참가자 상태·일시·마감·
/// "지금 뛰는 중"(STARTED) 표시. 크루장이면 open/cancel, 멤버면 참가 신청.
///
/// '레이스 시작'은 서버 STARTED 신호까지만 — 실제 GPS 트래킹 배선은 M2(D-1).
/// 보상 텍스트는 M3 Reward 소관(B2 미포함) — 자리만 안내 표기.
class SessionDetailScreen extends ConsumerStatefulWidget {
  const SessionDetailScreen({super.key, required this.sessionId});

  final int sessionId;

  @override
  ConsumerState<SessionDetailScreen> createState() =>
      _SessionDetailScreenState();
}

class _SessionDetailScreenState extends ConsumerState<SessionDetailScreen> {
  bool _busy = false;

  Future<void> _run(
    Future<SessionDetail> Function() command, {
    required String successMsg,
  }) async {
    setState(() => _busy = true);
    try {
      await command();
      ref.invalidate(sessionDetailProvider(widget.sessionId));
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(successMsg)));
      }
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(_messageFor(e))));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _messageFor(ApiException e) => switch (e.code) {
        'SESSION_STATE_INVALID' => '세션 상태가 바뀌었어요. 새로고침 해주세요.',
        'FORBIDDEN' => '권한이 없습니다.',
        'CREW_CLOSED' => '종료된 크루입니다.',
        'NOT_FOUND' => '세션을 찾을 수 없습니다.',
        _ => '요청을 처리하지 못했습니다: ${e.message}',
      };

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(sessionDetailProvider(widget.sessionId));
    return Scaffold(
      appBar: AppBar(title: const Text('레이스')),
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: detail.when(
          loading: () => const Center(
              child: CircularProgressIndicator(color: AppColors.ink)),
          error: (e, _) => _error(e),
          data: (session) => _body(session),
        ),
      ),
    );
  }

  Widget _error(Object e) {
    final msg = e is ApiException && e.code == 'FORBIDDEN'
        ? '이 크루의 멤버만 볼 수 있습니다.'
        : e is ApiException && e.code == 'NOT_FOUND'
            ? '존재하지 않는 세션입니다.'
            : '세션 정보를 불러오지 못했습니다.';
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(msg, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () =>
                ref.invalidate(sessionDetailProvider(widget.sessionId)),
            child: const Text('다시 시도'),
          ),
        ],
      ),
    );
  }

  Widget _body(SessionDetail session) {
    final crew = ref.watch(crewDetailProvider(session.crewId));
    final myId = ref.watch(authControllerProvider).profile?.id;
    final isLeader = crew.asData?.value.leader.userId == myId;
    final isClosed = crew.asData?.value.status == CrewStatus.closed;
    final myPart = _myParticipation(session, myId);

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
      children: [
        // 코스 미리보기 (placeholder 지도 — 실 타일 대기).
        CoursePolylineMap.forCourse(
          polyline: session.course.routePolyline,
          start: session.course.start,
          finish: session.course.finish,
          height: 200,
        ),
        const SizedBox(height: 16),

        // 코스명 + 세션 상태
        Row(
          children: [
            Expanded(
              child: Text(
                session.course.name,
                style: const TextStyle(
                    fontSize: 22, fontWeight: FontWeight.w800),
              ),
            ),
            StatusBadge(
              label: RaceFormat.sessionStatusLabel(session.status),
              color: RaceFormat.sessionStatusColor(session.status),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          RaceFormat.distance(session.course.distanceM),
          style: AppTypography.record.copyWith(
            fontSize: 15,
            color: AppColors.mutedAlt,
          ),
        ),

        // "지금 뛰는 중" — STARTED 참가자 존재 시(읽기 표시).
        if (session.hasRunners) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: AppColors.accentOrange.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Row(
              children: [
                const Icon(Icons.directions_run,
                    size: 18, color: AppColors.accentOrange),
                const SizedBox(width: 8),
                Text(
                  '지금 뛰는 중 · '
                  '${session.participants.where((p) => p.status.isRunningNow).length}명',
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    color: AppColors.ink,
                  ),
                ),
              ],
            ),
          ),
        ],

        const SizedBox(height: 20),
        _infoRow(Icons.schedule, '예정', RaceFormat.dateTime(session.scheduledAt)),
        const SizedBox(height: 8),
        _infoRow(Icons.upload_file, '업로드 마감',
            RaceFormat.dateTime(session.uploadDeadline)),
        const SizedBox(height: 8),
        // 보상 텍스트는 M3(Reward) — 자리만 표기.
        _infoRow(Icons.emoji_events_outlined, '보상', '다음 업데이트에서 제공 (M3)'),

        const SizedBox(height: 24),
        _actions(session, isLeader: isLeader, isClosed: isClosed, myPart: myPart),

        // 결과 진입 — 집계 중(FINALIZING)이면 결과 대기 화면(C2), 완료면 순위표(C1).
        if (session.status == RaceStatus.finalizing ||
            session.status == RaceStatus.completed) ...[
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: () => context.push('/sessions/${session.id}/result'),
            icon: const Icon(Icons.leaderboard),
            label: Text(session.status == RaceStatus.completed
                ? '결과 · 순위 보기'
                : '결과 확인 (집계 중)'),
          ),
        ],

        const SizedBox(height: 28),
        const Text(
          '참가자',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            letterSpacing: 1,
            color: Color(0xFF9AA18C),
          ),
        ),
        const SizedBox(height: 8),
        if (session.participants.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Text('아직 참가 신청한 러너가 없어요.',
                style: TextStyle(color: AppColors.muted)),
          )
        else
          ...session.participants.map((p) => _ParticipantRow(participant: p)),
      ],
    );
  }

  ParticipantView? _myParticipation(SessionDetail session, int? myId) {
    for (final p in session.participants) {
      if (p.userId == myId) return p;
    }
    return null;
  }

  Widget _infoRow(IconData icon, String label, String value) {
    return Row(
      children: [
        Icon(icon, size: 18, color: AppColors.muted),
        const SizedBox(width: 10),
        SizedBox(
          width: 84,
          child: Text(label,
              style: const TextStyle(fontSize: 13, color: AppColors.muted)),
        ),
        Expanded(
          child: Text(value,
              style: const TextStyle(
                  fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.ink)),
        ),
      ],
    );
  }

  Widget _actions(
    SessionDetail session, {
    required bool isLeader,
    required bool isClosed,
    required ParticipantView? myPart,
  }) {
    if (isClosed) {
      return const SizedBox.shrink();
    }
    final buttons = <Widget>[];

    // 크루장: 발행(open) / 취소(cancel).
    if (isLeader) {
      if (session.status.canOpen) {
        buttons.add(FilledButton.icon(
          onPressed: _busy
              ? null
              : () => _run(
                    () => ref
                        .read(sessionRepositoryProvider)
                        .open(session.id),
                    successMsg: '세션을 발행했어요. 이제 참가 신청을 받아요.',
                  ),
          icon: const Icon(Icons.campaign),
          label: const Text('세션 발행 (참가 개방)'),
        ));
      }
      if (session.status.canCancel) {
        buttons.add(OutlinedButton.icon(
          onPressed: _busy ? null : () => _confirmCancel(session),
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.accentPink,
            side: const BorderSide(color: AppColors.accentPink),
            minimumSize: const Size.fromHeight(52),
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16)),
          ),
          icon: const Icon(Icons.cancel_outlined),
          label: const Text('세션 취소'),
        ));
      }
    }

    // 멤버: 참가 신청 (OPEN·미참가). 참가 취소(unregister)는 계약 미제공 → 미노출.
    if (myPart == null && session.status.canRegister) {
      buttons.add(FilledButton.icon(
        onPressed: _busy
            ? null
            : () => _run(
                  () => ref
                      .read(sessionRepositoryProvider)
                      .register(session.id),
                  successMsg: '참가 신청 완료!',
                ),
        icon: const Icon(Icons.how_to_reg),
        label: const Text('참가 신청'),
      ));
    }

    // 참가자: 내 상태 + '레이스 시작' 신호(트래킹은 M2).
    if (myPart != null) {
      buttons.add(_MyStatusChip(status: myPart.status));
      final canSignalStart = session.status == RaceStatus.open ||
          session.status == RaceStatus.running;
      if (canSignalStart && myPart.status != ParticipationStatus.started) {
        buttons.add(_RaceStartEntry(
          busy: _busy,
          onSignal: () => _run(
            () => ref.read(sessionRepositoryProvider).start(session.id),
            successMsg: '레이스 시작 신호를 보냈어요.',
          ),
        ));
      }
    }

    if (buttons.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < buttons.length; i++) ...[
          if (i > 0) const SizedBox(height: 12),
          buttons[i],
        ],
      ],
    );
  }

  Future<void> _confirmCancel(SessionDetail session) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('세션 취소'),
        content: const Text('이 레이스 세션을 취소할까요? 취소하면 되돌릴 수 없어요.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.of(ctx).pop(false),
              child: const Text('닫기')),
          FilledButton(
            style:
                FilledButton.styleFrom(backgroundColor: AppColors.accentPink),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('세션 취소'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await _run(
      () => ref.read(sessionRepositoryProvider).cancel(session.id),
      successMsg: '세션을 취소했어요.',
    );
  }
}

/// 내 참가 상태 칩.
class _MyStatusChip extends StatelessWidget {
  const _MyStatusChip({required this.status});

  final ParticipationStatus status;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          const Icon(Icons.person_pin_circle_outlined,
              color: AppColors.ink),
          const SizedBox(width: 10),
          const Text('내 참가 상태',
              style: TextStyle(fontWeight: FontWeight.w600)),
          const Spacer(),
          StatusBadge(
            label: RaceFormat.participationLabel(status),
            color: RaceFormat.participationColor(status),
          ),
        ],
      ),
    );
  }
}

/// '레이스 시작' 진입점 — 서버 STARTED 신호 버튼 + 트래킹 M2 안내(D-1).
/// 실제 GPS 트래킹(AndroidForegroundTracker)·로컬 상태머신은 M2에서 배선한다.
class _RaceStartEntry extends StatelessWidget {
  const _RaceStartEntry({required this.busy, required this.onSignal});

  final bool busy;
  final VoidCallback onSignal;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.ink,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          FilledButton.icon(
            onPressed: busy ? null : onSignal,
            icon: const Icon(Icons.play_arrow),
            label: const Text('레이스 시작'),
          ),
          const SizedBox(height: 8),
          const Text(
            '지금은 시작 신호만 서버에 보냅니다. 실제 GPS 트래킹은 다음 '
            '업데이트(M2)에서 연결돼요.',
            style: TextStyle(color: AppColors.muted, fontSize: 12),
          ),
        ],
      ),
    );
  }
}

class _ParticipantRow extends StatelessWidget {
  const _ParticipantRow({required this.participant});

  final ParticipantView participant;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: AppColors.ink,
            child: Text(
              participant.nickname.characters.first,
              style: const TextStyle(
                  color: AppColors.lime, fontWeight: FontWeight.w700),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              participant.nickname,
              style: const TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: AppColors.ink,
              ),
            ),
          ),
          StatusBadge(
            label: RaceFormat.participationLabel(participant.status),
            color: RaceFormat.participationColor(participant.status),
          ),
        ],
      ),
    );
  }
}
