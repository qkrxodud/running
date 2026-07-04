import 'package:flutter/material.dart';

import '../../app/app_theme.dart';

/// 디자인(1a 라임)의 원형 이니셜 아바타. userId 로 색을 결정론적으로 배정한다
/// (마커·페이스 구분 팔레트 — accent 4색 + ink).
class CrewAvatar extends StatelessWidget {
  const CrewAvatar({
    super.key,
    required this.userId,
    required this.nickname,
    this.size = 40,
  });

  final int userId;
  final String nickname;
  final double size;

  static const _palette = [
    (AppColors.accentOrange, Colors.white),
    (AppColors.accentPurple, Colors.white),
    (AppColors.accentCyan, AppColors.ink),
    (AppColors.accentPink, Colors.white),
    (AppColors.ink, AppColors.lime),
  ];

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = _palette[userId.abs() % _palette.length];
    final initial = nickname.trim().isEmpty
        ? '?'
        : nickname.trim().characters.first;
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(color: bg, shape: BoxShape.circle),
      child: Text(
        initial,
        style: TextStyle(
          color: fg,
          fontWeight: FontWeight.w800,
          fontSize: size * 0.4,
        ),
      ),
    );
  }
}
