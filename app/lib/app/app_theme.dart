import 'package:flutter/material.dart';

/// 1a 라임 디자인 토큰 (dc.html 추출). 화면 뼈대(배치 B)에서 색·타이포를 이 토큰으로
/// 통일한다. 폰트(Pretendard/Space Grotesk) 등록은 발급물·화면 작업 시 확장.
class AppColors {
  const AppColors._();

  static const ink = Color(0xFF14170F); // 진회흑: 기본 텍스트, 다크 서피스
  static const lime = Color(0xFFC7F94E); // 브랜드 포인트, CTA
  static const bg = Color(0xFFF4F6EE); // 배경
  static const bgAlt = Color(0xFFE7EAE1); // 배경 대안
  static const muted = Color(0xFF8A917F); // 보조 텍스트·비활성

  // 참가자 구분(리플레이 마커·페이스), 상태 색
  static const accentPurple = Color(0xFF9B7BFF);
  static const accentCyan = Color(0xFF4CD6D0);
  static const accentOrange = Color(0xFFFF9F45);
  static const accentPink = Color(0xFFFF6BA6);
}

class AppTheme {
  const AppTheme._();

  static ThemeData light() {
    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: AppColors.lime,
        primary: AppColors.lime,
        onPrimary: AppColors.ink,
      ),
      scaffoldBackgroundColor: AppColors.bg,
    );
  }
}
