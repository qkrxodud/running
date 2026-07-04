import 'package:flutter/material.dart';

/// 1a 라임 디자인 토큰 (dc.html 추출 — flutter-client 스킬 표와 동일).
/// 임의 색 금지 — 디자인에 없는 화면도 이 토큰으로 룩앤필 유지.
class AppColors {
  const AppColors._();

  static const ink = Color(0xFF14170F); // 진회흑: 기본 텍스트, 다크 서피스
  static const lime = Color(0xFFC7F94E); // 브랜드 포인트, CTA
  static const bg = Color(0xFFF4F6EE); // 배경
  static const bgAlt = Color(0xFFE7EAE1); // 배경 대안
  static const muted = Color(0xFF8A917F); // 보조 텍스트·비활성
  static const mutedAlt = Color(0xFF7E8676);

  // 참가자 구분(리플레이 마커·페이스), 상태 색
  static const accentPurple = Color(0xFF9B7BFF);
  static const accentCyan = Color(0xFF4CD6D0);
  static const accentOrange = Color(0xFFFF9F45);
  static const accentPink = Color(0xFFFF6BA6);
}

/// 타이포 헬퍼.
///
/// 주의(보류): Pretendard·Space Grotesk 폰트 파일이 저장소에 없어 pubspec 등록
/// 불가 — 시스템 폰트로 대체 중. 폰트 자산 확보 시 fontFamily 만 지정하면
/// 전 화면에 반영되도록 여기로 단일화한다. 기록·숫자는 반드시 Space Grotesk
/// (스킬 규칙 — 스포티한 인상의 근원).
class AppTypography {
  const AppTypography._();

  /// 기록·숫자·코드용 (Space Grotesk 대체 — tabular figures 로 정렬 보장).
  static const record = TextStyle(
    fontWeight: FontWeight.w700,
    fontFeatures: [FontFeature.tabularFigures()],
    letterSpacing: 1.5,
  );

  static const headline = TextStyle(fontWeight: FontWeight.w800);
}

class AppTheme {
  const AppTheme._();

  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.lime,
      primary: AppColors.lime,
      onPrimary: AppColors.ink,
      surface: AppColors.bg,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.bg,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.bg,
        foregroundColor: AppColors.ink,
        elevation: 0,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: AppColors.ink,
          fontSize: 20,
          fontWeight: FontWeight.w800,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.lime,
          foregroundColor: AppColors.ink,
          minimumSize: const Size.fromHeight(56),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide.none,
        ),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        hintStyle: const TextStyle(color: AppColors.muted),
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        margin: EdgeInsets.zero,
      ),
    );
  }
}
