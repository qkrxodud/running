import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/app_theme.dart';
import '../../core/model/api_error.dart';
import '../../core/model/replay_dtos.dart';
import '../../core/replay/replay_controller.dart';
import 'replay_map.dart';
import 'replay_palette.dart';
import 'replay_providers.dart';

/// 리플레이 뷰어 (M3-B) — replay-api §1 소비. 전원 마커 동시 이동·배속·시킹·추월·
/// 페이스 색상·GPS 유실 구분·DNF 경로. 상태별 UI(GENERATING/FAILED/READY) +
/// 스키마 버전 게이트(초과 시 "앱 업데이트 필요", 크래시 금지).
///
/// 디자인: 1a 라임 "크루 리플레이/리플레이 뷰어"(다크 서피스). 참가자=accent
/// 팔레트, 기록 숫자=Space Grotesk(AppTypography.record).
class ReplayScreen extends ConsumerStatefulWidget {
  const ReplayScreen({super.key, required this.sessionId});

  final int sessionId;

  @override
  ConsumerState<ReplayScreen> createState() => _ReplayScreenState();
}

class _ReplayScreenState extends ConsumerState<ReplayScreen>
    with SingleTickerProviderStateMixin {
  late final Ticker _ticker;
  Duration _last = Duration.zero;

  /// 재생 상태(순수 컨트롤러). READY 스냅샷 로드 시 duration 으로 초기화.
  ReplayPlayback? _playback;
  int? _initializedFor; // duration 재초기화 감지용

  @override
  void initState() {
    super.initState();
    _ticker = createTicker(_onTick)..start();
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  void _onTick(Duration elapsed) {
    final delta = elapsed - _last;
    _last = elapsed;
    final pb = _playback;
    if (pb == null || !pb.playing) return;
    final next = pb.advance(delta.inMilliseconds);
    if (!identical(next, pb)) setState(() => _playback = next);
  }

  void _ensurePlayback(int durationMs) {
    if (_initializedFor != durationMs) {
      _initializedFor = durationMs;
      _playback = ReplayPlayback(durationMs: durationMs);
    }
  }

  void _update(ReplayPlayback pb) => setState(() => _playback = pb);

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(replaySnapshotProvider(widget.sessionId));
    return Scaffold(
      backgroundColor: const Color(0xFF0E120C),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0E120C),
        foregroundColor: Colors.white,
        title: const Text('리플레이',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800)),
      ),
      body: SafeArea(
        child: async.when(
          loading: () => const Center(
              child: CircularProgressIndicator(color: AppColors.lime)),
          error: (e, _) => _errorState(e),
          data: _dataState,
        ),
      ),
    );
  }

  Widget _dataState(ReplaySnapshotResponse res) {
    if (res.isGenerating) {
      return _StatusMessage(
        icon: Icons.movie_creation_outlined,
        title: '리플레이 만드는 중',
        body: '모든 기록을 모아 리플레이를 만들고 있어요.\n잠시 후 다시 확인해 주세요.',
        actionLabel: '새로고침',
        onAction: _refresh,
      );
    }
    if (res.isFailed) {
      return _StatusMessage(
        icon: Icons.error_outline,
        title: '리플레이를 만들지 못했어요',
        body: '문제가 생겨 리플레이 생성에 실패했어요.\n다시 시도하거나 잠시 후 확인해 주세요.',
        actionLabel: '다시 시도',
        onAction: _refresh,
      );
    }
    if (res.isReady && !res.isVersionSupported) {
      // 버전 게이트 — 미지 상위 스키마. 크래시 대신 업데이트 안내.
      return const _StatusMessage(
        icon: Icons.system_update,
        title: '앱 업데이트가 필요해요',
        body: '이 리플레이는 최신 버전에서 볼 수 있어요.\n앱을 업데이트한 뒤 다시 확인해 주세요.',
      );
    }
    if (!res.isReady) {
      return _StatusMessage(
        icon: Icons.hourglass_empty,
        title: '리플레이가 아직 없어요',
        body: '리플레이가 준비되면 여기에서 볼 수 있어요.',
        actionLabel: '새로고침',
        onAction: _refresh,
      );
    }

    final snapshot = res.payload!;
    _ensurePlayback(snapshot.durationMs);
    final pb = _playback!;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        ReplayMap(snapshot: snapshot, positionMs: pb.positionMs),
        const SizedBox(height: 16),
        _Timeline(
          playback: pb,
          overtakes: snapshot.overtakes,
          onSeek: (ms) => _update(pb.seek(ms)),
        ),
        const SizedBox(height: 8),
        _TimeReadout(playback: pb),
        const SizedBox(height: 12),
        _Controls(
          playback: pb,
          onToggle: () => _update(pb.togglePlay()),
          onCycleSpeed: () => _update(pb.cycleSpeed()),
          onRestart: () => _update(pb.restart()),
        ),
        const SizedBox(height: 20),
        _OvertakeBanner(snapshot: snapshot, response: res, positionMs: pb.positionMs),
        const SizedBox(height: 12),
        _Legend(snapshot: snapshot, response: res),
      ],
    );
  }

  Widget _errorState(Object e) {
    final t = replayErrorText(e);
    return _StatusMessage(
      icon: t.is404 ? Icons.hourglass_empty : Icons.wifi_off,
      title: t.title,
      body: t.body,
      actionLabel: '다시 시도',
      onAction: _refresh,
    );
  }

  void _refresh() => ref.invalidate(replaySnapshotProvider(widget.sessionId));
}

/// 리플레이 조회 오류 → 안내 문구(순수·테스트 가능). 404(스냅샷 미생성/미준비)와
/// 그 외(네트워크·서버)를 code 기준으로 구분한다.
({bool is404, String title, String body}) replayErrorText(Object e) {
  final is404 = e is ApiException && e.statusCode == 404;
  return is404
      ? (
          is404: true,
          title: '리플레이가 아직 없어요',
          body: '이 세션은 리플레이가 없거나 아직 준비되지 않았어요.'
        )
      : (
          is404: false,
          title: '리플레이를 불러오지 못했어요',
          body: '네트워크를 확인하고 다시 시도해 주세요.'
        );
}

/// 시간축 — 재생 위치(플레이헤드) + 추월 지점 틱. 탭·드래그로 시킹.
class _Timeline extends StatelessWidget {
  const _Timeline({
    required this.playback,
    required this.overtakes,
    required this.onSeek,
  });

  final ReplayPlayback playback;
  final List<Overtake> overtakes;
  final ValueChanged<int> onSeek;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        final duration = playback.durationMs <= 0 ? 1 : playback.durationMs;
        void seekAt(double dx) =>
            onSeek(((dx / width).clamp(0, 1) * duration).round());

        return GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: (d) => seekAt(d.localPosition.dx),
          onHorizontalDragUpdate: (d) => seekAt(d.localPosition.dx),
          child: SizedBox(
            height: 28,
            child: Stack(
              alignment: Alignment.centerLeft,
              children: [
                // 트랙.
                Container(
                  height: 5,
                  decoration: BoxDecoration(
                    color: const Color(0xFF2A3020),
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
                // 진행분.
                FractionallySizedBox(
                  widthFactor: playback.progress.clamp(0, 1),
                  child: Container(
                    height: 5,
                    decoration: BoxDecoration(
                      color: AppColors.lime,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
                // 추월 틱(타임라인 위).
                for (final o in overtakes)
                  Positioned(
                    left: (o.tMs / duration).clamp(0, 1) * width - 4,
                    child: const Icon(Icons.bolt,
                        size: 14, color: AppColors.lime),
                  ),
                // 플레이헤드.
                Positioned(
                  left: (playback.progress.clamp(0, 1) * width - 7)
                      .clamp(0, width - 14),
                  child: Container(
                    width: 14,
                    height: 14,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                      border: Border.all(color: AppColors.lime, width: 2),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _TimeReadout extends StatelessWidget {
  const _TimeReadout({required this.playback});
  final ReplayPlayback playback;

  static String _fmt(int ms) {
    final s = ms ~/ 1000;
    final m = s ~/ 60;
    return '${m.toString().padLeft(2, '0')}:${(s % 60).toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(_fmt(playback.positionMs),
            style: AppTypography.record.copyWith(
                color: Colors.white, fontSize: 14, letterSpacing: 0)),
        Text(_fmt(playback.durationMs),
            style: AppTypography.record.copyWith(
                color: AppColors.muted, fontSize: 14, letterSpacing: 0)),
      ],
    );
  }
}

class _Controls extends StatelessWidget {
  const _Controls({
    required this.playback,
    required this.onToggle,
    required this.onCycleSpeed,
    required this.onRestart,
  });

  final ReplayPlayback playback;
  final VoidCallback onToggle;
  final VoidCallback onCycleSpeed;
  final VoidCallback onRestart;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        IconButton(
          onPressed: onRestart,
          icon: const Icon(Icons.replay, color: Colors.white),
          tooltip: '처음부터',
        ),
        const Spacer(),
        // 재생/일시정지.
        GestureDetector(
          onTap: onToggle,
          child: Container(
            width: 64,
            height: 64,
            decoration: const BoxDecoration(
                color: AppColors.lime, shape: BoxShape.circle),
            child: Icon(
              playback.playing ? Icons.pause : Icons.play_arrow,
              color: AppColors.ink,
              size: 34,
            ),
          ),
        ),
        const Spacer(),
        // 배속(1x/2x/4x 순환).
        OutlinedButton(
          onPressed: onCycleSpeed,
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.lime,
            side: const BorderSide(color: AppColors.lime),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          child: Text('${playback.speed.toStringAsFixed(0)}x',
              style: AppTypography.record
                  .copyWith(fontSize: 15, letterSpacing: 0)),
        ),
      ],
    );
  }
}

/// 추월 발생 순간 배너 — 타임라인 위치가 추월 시각에 근접하면 강조 표시.
class _OvertakeBanner extends StatelessWidget {
  const _OvertakeBanner({
    required this.snapshot,
    required this.response,
    required this.positionMs,
  });

  final ReplaySnapshot snapshot;
  final ReplaySnapshotResponse response;
  final int positionMs;

  @override
  Widget build(BuildContext context) {
    Overtake? active;
    for (final o in snapshot.overtakes) {
      if ((positionMs - o.tMs).abs() <= 900) {
        active = o;
        break;
      }
    }
    if (active == null) return const SizedBox.shrink();
    final passer = response.displayName(active.passerUserId);
    final passed = response.displayName(active.passedUserId);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.lime,
        borderRadius: BorderRadius.circular(100),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.bolt, color: AppColors.ink, size: 18),
          const SizedBox(width: 6),
          Text('추월! $passer → $passed',
              style: const TextStyle(
                  color: AppColors.ink, fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }
}

/// 범례 — 참가자 색·이름(조인) + 페이스 색상 + GPS 유실 표시.
class _Legend extends StatelessWidget {
  const _Legend({required this.snapshot, required this.response});

  final ReplaySnapshot snapshot;
  final ReplaySnapshotResponse response;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('참가자',
            style: TextStyle(
                color: Color(0xFF9AA18C),
                fontSize: 12,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.2)),
        const SizedBox(height: 8),
        Wrap(
          spacing: 12,
          runSpacing: 8,
          children: [
            for (var i = 0; i < snapshot.participants.length; i++)
              _chip(
                ReplayPalette.forParticipant(i),
                '${response.displayName(snapshot.participants[i].userId)}'
                '${snapshot.participants[i].isDnf ? ' (미완주)' : ''}',
              ),
          ],
        ),
        const SizedBox(height: 14),
        Row(
          children: [
            const Text('페이스',
                style: TextStyle(color: AppColors.muted, fontSize: 12)),
            const SizedBox(width: 8),
            const Text('빠름',
                style: TextStyle(color: AppColors.muted, fontSize: 11)),
            const SizedBox(width: 4),
            for (final c in ReplayPalette.paceBuckets)
              Container(
                  width: 16,
                  height: 8,
                  color: c),
            const SizedBox(width: 4),
            const Text('느림',
                style: TextStyle(color: AppColors.muted, fontSize: 11)),
          ],
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            SizedBox(
              width: 24,
              child: CustomPaint(
                  size: const Size(24, 3), painter: _DashPainter()),
            ),
            const SizedBox(width: 8),
            const Text('GPS 유실 구간(보간)',
                style: TextStyle(color: AppColors.muted, fontSize: 12)),
          ],
        ),
      ],
    );
  }

  Widget _chip(Color color, String label) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
              width: 12,
              height: 12,
              decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
          const SizedBox(width: 6),
          Text(label,
              style: const TextStyle(color: Colors.white, fontSize: 13)),
        ],
      );
}

class _DashPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = ReplayPalette.gap
      ..strokeWidth = 3;
    var x = 0.0;
    while (x < size.width) {
      canvas.drawLine(
          Offset(x, size.height / 2), Offset(x + 5, size.height / 2), paint);
      x += 9;
    }
  }

  @override
  bool shouldRepaint(_DashPainter old) => false;
}

class _StatusMessage extends StatelessWidget {
  const _StatusMessage({
    required this.icon,
    required this.title,
    required this.body,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String body;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 56, color: AppColors.lime),
            const SizedBox(height: 20),
            Text(title,
                textAlign: TextAlign.center,
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.w800)),
            const SizedBox(height: 10),
            Text(body,
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.muted, fontSize: 14)),
            if (actionLabel != null) ...[
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: onAction,
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.lime,
                  side: const BorderSide(color: AppColors.lime),
                ),
                icon: const Icon(Icons.refresh),
                label: Text(actionLabel!),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
