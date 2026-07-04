import 'package:flutter/material.dart';

import '../../app/app_theme.dart';
import '../../core/model/race_dtos.dart';

/// 세션·참가 상태의 표시 라벨/색 매핑 (1a 라임 토큰). 화면 공통.
class RaceFormat {
  const RaceFormat._();

  static String sessionStatusLabel(RaceStatus s) => switch (s) {
        RaceStatus.draft => '준비 중',
        RaceStatus.open => '모집 중',
        RaceStatus.running => '진행 중',
        RaceStatus.finalizing => '집계 중',
        RaceStatus.completed => '완료',
        RaceStatus.cancelled => '취소됨',
        RaceStatus.unknown => '알 수 없음',
      };

  static Color sessionStatusColor(RaceStatus s) => switch (s) {
        RaceStatus.open => AppColors.lime,
        RaceStatus.running => AppColors.accentOrange,
        RaceStatus.finalizing => AppColors.accentPurple,
        RaceStatus.draft => AppColors.muted,
        RaceStatus.completed => AppColors.muted,
        RaceStatus.cancelled => AppColors.muted,
        RaceStatus.unknown => AppColors.muted,
      };

  static String participationLabel(ParticipationStatus s) => switch (s) {
        ParticipationStatus.registered => '신청',
        ParticipationStatus.started => '지금 뛰는 중',
        ParticipationStatus.finished => '완주',
        ParticipationStatus.dnf => '미완주',
        ParticipationStatus.dns => '불참',
        ParticipationStatus.withdrawn => '탈퇴',
        ParticipationStatus.unknown => '-',
      };

  static Color participationColor(ParticipationStatus s) => switch (s) {
        ParticipationStatus.started => AppColors.accentOrange,
        ParticipationStatus.finished => AppColors.lime,
        ParticipationStatus.registered => AppColors.accentCyan,
        _ => AppColors.muted,
      };

  /// 거리 표기 — 5000m → "5.0km", 1200m → "1.2km".
  static String distance(int meters) =>
      '${(meters / 1000).toStringAsFixed(1)}km';

  /// 예정 일시 — 로컬 변환 후 "7월 10일 (목) 21:00" 형태.
  static String dateTime(DateTime utc) {
    final l = utc.toLocal();
    const weekdays = ['월', '화', '수', '목', '금', '토', '일'];
    final wd = weekdays[(l.weekday - 1) % 7];
    final hh = l.hour.toString().padLeft(2, '0');
    final mm = l.minute.toString().padLeft(2, '0');
    return '${l.month}월 ${l.day}일 ($wd) $hh:$mm';
  }
}

/// 세션/참가 상태 pill 배지.
class StatusBadge extends StatelessWidget {
  const StatusBadge({super.key, required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    // 밝은 lime 계열은 ink 텍스트, 그 외는 흰 텍스트로 대비 확보.
    final onColor =
        color == AppColors.lime ? AppColors.ink : Colors.white;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(100),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          color: onColor,
        ),
      ),
    );
  }
}
