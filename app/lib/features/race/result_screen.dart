import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/app_theme.dart';
import '../../core/model/api_error.dart';
import '../../core/model/track_dtos.dart';
import '../../data/track_repository.dart';
import 'race_format.dart';
import 'race_providers.dart';
import 'result_format.dart';

/// 세션 결과 화면 (C1 순위표 + C2 결과 대기) — track-api §3 소비.
///
/// 디자인: 1a 라임 dc.html "결과·순위·보상" 섹션(다크 서피스). 기록 숫자는
/// Space Grotesk(AppTypography.record). 순위는 서버 산정값 그대로 표시
/// (동률 공동순위 1,1,3). PB 뱃지는 `is_pb` true 만. DNF/DNS 하단 + rank/record/
/// pace 키부재=null 안전 렌더(P46-1). 리플레이 진입은 M3 placeholder.
class SessionResultScreen extends ConsumerWidget {
  const SessionResultScreen({super.key, required this.sessionId});

  final int sessionId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final result = ref.watch(sessionResultProvider(sessionId));
    return Scaffold(
      backgroundColor: AppColors.ink,
      appBar: AppBar(
        backgroundColor: AppColors.ink,
        foregroundColor: Colors.white,
        title: const Text('레이스 결과',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800)),
      ),
      body: SafeArea(
        child: result.when(
          loading: () => const Center(
              child: CircularProgressIndicator(color: AppColors.lime)),
          error: (e, _) => _error(context, ref, e),
          data: (outcome) => switch (outcome) {
            ResultReady(:final result) => _RankingView(result: result),
            ResultPending() => _WaitingView(sessionId: sessionId),
          },
        ),
      ),
    );
  }

  Widget _error(BuildContext context, WidgetRef ref, Object e) {
    // CANCELLED 세션은 결과 미생성(track-api §3 → 404).
    final msg = e is ApiException && e.statusCode == 404
        ? '이 세션의 결과가 없습니다 (취소되었거나 존재하지 않아요).'
        : '결과를 불러오지 못했습니다.';
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(msg, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () => ref.invalidate(sessionResultProvider(sessionId)),
            child: const Text('다시 시도',
                style: TextStyle(color: AppColors.lime)),
          ),
        ],
      ),
    );
  }
}

/// C2 — 결과 대기(RESULT_NOT_READY). "업로드 완료 — 다른 참가자를 기다리는 중".
///
/// **수치 카운트 없음(R-009)**: 서버는 participation 을 세션 확정 시 일괄 전이하므로
/// 대기 구간엔 개별 업로드 완료를 나타내는 파생값이 존재하지 않는다(계약에 per-
/// participant 업로드 플래그 부재). 계약에 없는 진행률을 UI 가 지어내지 않고,
/// 정성 문구 + 마감 시각만 표시한다. STARTED("지금 뛰는 중")는 실시간 신호라 유지.
class _WaitingView extends ConsumerWidget {
  const _WaitingView({required this.sessionId});

  final int sessionId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(sessionDetailProvider(sessionId));
    final session = detail.asData?.value;
    // 실시간 신호(유효): 아직 뛰는 중인 참가자 수.
    final runningNow = session == null
        ? 0
        : session.participants.where((p) => p.status.isRunningNow).length;

    return RefreshIndicator(
      color: AppColors.lime,
      onRefresh: () async {
        ref.invalidate(sessionResultProvider(sessionId));
        ref.invalidate(sessionDetailProvider(sessionId));
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(24, 80, 24, 24),
        children: [
          const Icon(Icons.cloud_done_outlined,
              size: 56, color: AppColors.lime),
          const SizedBox(height: 20),
          const Text(
            '업로드 완료',
            textAlign: TextAlign.center,
            style: TextStyle(
                color: Colors.white, fontSize: 24, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          const Text(
            '다른 참가자들을 기다리는 중이에요.\n전원 완료 또는 마감 시각에 순위가 확정됩니다.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.muted, fontSize: 15),
          ),
          if (session != null) ...[
            const SizedBox(height: 16),
            Center(
              child: Text(
                '업로드 마감 · ${RaceFormat.dateTime(session.uploadDeadline)}',
                style:
                    const TextStyle(color: AppColors.mutedAlt, fontSize: 13),
              ),
            ),
          ],
          // 실시간 신호(STARTED)만 표시 — 최종화 상태를 업로드 대리로 쓰지 않는다.
          if (runningNow > 0) ...[
            const SizedBox(height: 12),
            Center(
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.directions_run,
                      size: 16, color: AppColors.accentOrange),
                  const SizedBox(width: 6),
                  Text('지금 뛰는 중 · $runningNow명',
                      style: const TextStyle(
                          color: AppColors.accentOrange,
                          fontSize: 13,
                          fontWeight: FontWeight.w700)),
                ],
              ),
            ),
          ],
          const SizedBox(height: 28),
          Center(
            child: OutlinedButton.icon(
              onPressed: () {
                ref.invalidate(sessionResultProvider(sessionId));
                ref.invalidate(sessionDetailProvider(sessionId));
              },
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.lime,
                side: const BorderSide(color: AppColors.lime),
              ),
              icon: const Icon(Icons.refresh),
              label: const Text('결과 다시 확인'),
            ),
          ),
        ],
      ),
    );
  }
}

/// C1 — 순위표.
class _RankingView extends StatelessWidget {
  const _RankingView({required this.result});

  final RaceResultResponse result;

  @override
  Widget build(BuildContext context) {
    final finishedCount =
        result.entries.where((e) => e.status.isFinished).length;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 6, 20, 24),
      children: [
        Center(
          child: Text(
            '${result.course.name} · $finishedCount명 완주',
            style: const TextStyle(color: AppColors.mutedAlt, fontSize: 13),
          ),
        ),
        const SizedBox(height: 16),
        const Text(
          '전체 순위',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.4,
            color: Color(0xFF9AA18C),
          ),
        ),
        const SizedBox(height: 10),
        ...result.entries.map((e) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _ResultRow(entry: e),
            )),
        const SizedBox(height: 18),
        // 리플레이 진입 — M3 placeholder(미지원 안내).
        _ReplayPlaceholder(),
      ],
    );
  }
}

class _ResultRow extends StatelessWidget {
  const _ResultRow({required this.entry});

  final ResultEntry entry;

  @override
  Widget build(BuildContext context) {
    final isFinished = entry.status.isFinished;
    // 완주=강조 서피스, DNF/DNS=하단 흐린 서피스.
    final surface = isFinished ? const Color(0xFF14180F) : const Color(0xFF10130D);
    final rankColor = entry.rank == 1 ? AppColors.lime : AppColors.mutedAlt;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: surface,
        borderRadius: BorderRadius.circular(14),
        border: entry.isPb
            ? Border.all(color: AppColors.lime, width: 1)
            : null,
      ),
      child: Row(
        children: [
          SizedBox(
            width: 22,
            child: Text(
              ResultFormat.rank(entry.rank),
              style: AppTypography.record.copyWith(
                fontSize: 14,
                color: isFinished ? rankColor : AppColors.muted,
                letterSpacing: 0,
              ),
            ),
          ),
          const SizedBox(width: 8),
          CircleAvatar(
            radius: 16,
            backgroundColor: isFinished ? AppColors.accentCyan : AppColors.muted,
            child: Text(
              entry.nickname.isEmpty ? '?' : entry.nickname.characters.first,
              style: const TextStyle(
                  color: AppColors.ink,
                  fontWeight: FontWeight.w800,
                  fontSize: 12),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  entry.nickname,
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.w700),
                ),
                if (entry.isPb)
                  const Padding(
                    padding: EdgeInsets.only(top: 2),
                    child: Text('🏅 개인 최고 기록',
                        style: TextStyle(color: AppColors.lime, fontSize: 11)),
                  )
                else if (!isFinished)
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Text(ResultFormat.entryStatus(entry.status),
                        style:
                            const TextStyle(color: AppColors.muted, fontSize: 11)),
                  ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              // record_time_s / avg_pace_s_per_km 키부재=null 안전(P46-1).
              Text(
                ResultFormat.time(entry.recordTimeS),
                style: AppTypography.record.copyWith(
                    fontSize: 14, color: Colors.white, letterSpacing: 0),
              ),
              const SizedBox(height: 2),
              Text(
                ResultFormat.pace(entry.avgPaceSPerKm),
                style: const TextStyle(color: Color(0xFF7E8676), fontSize: 11),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// 리플레이 다시보기 — M3(replay-api) 대기. 눌러도 "준비 중" 안내(자리 확보).
class _ReplayPlaceholder extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: 0.5,
      child: Container(
        height: 52,
        decoration: BoxDecoration(
          color: AppColors.lime,
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.replay, color: AppColors.ink, size: 20),
            SizedBox(width: 8),
            Text('리플레이 다시보기 (다음 업데이트 · M3)',
                style: TextStyle(
                    color: AppColors.ink, fontWeight: FontWeight.w700)),
          ],
        ),
      ),
    );
  }
}
