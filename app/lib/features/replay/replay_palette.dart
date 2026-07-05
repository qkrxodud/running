import 'package:flutter/material.dart';

import '../../app/app_theme.dart';

/// 리플레이 색상 규약 (1a 라임 토큰) — 참가자 구분 accent 팔레트 + 페이스 버킷 색.
class ReplayPalette {
  const ReplayPalette._();

  /// 참가자 구분 색(마커 정체성) — 퍼플·시안·오렌지·핑크 순환(디자인 accent).
  static const List<Color> identity = [
    AppColors.accentPurple,
    AppColors.accentCyan,
    AppColors.accentOrange,
    AppColors.accentPink,
  ];

  static Color forParticipant(int index) =>
      identity[index % identity.length];

  /// 구간 페이스 색상 버킷 — 빠름(라임)→느림(레드). color_bucket 인덱스로 접근.
  static const List<Color> paceBuckets = [
    AppColors.lime, // 0 가장 빠름
    Color(0xFF9BE86A),
    AppColors.accentCyan,
    AppColors.accentOrange,
    AppColors.accentPink, // 4 가장 느림
    Color(0xFFFF5A5A),
  ];

  static Color forPaceBucket(int bucket) =>
      paceBuckets[bucket.clamp(0, paceBuckets.length - 1)];

  /// GPS 유실 보간 구간 표시색(점선) — 실측과 시각적으로 구분.
  static const Color gap = Color(0xFF6B7263);
}
