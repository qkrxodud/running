import 'package:flutter/material.dart';

import '../../app/app_theme.dart';

/// 부트스트랩 대기 화면 (토큰 복원 중). AuthStatus.unknown 동안 표시.
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: AppColors.ink,
      body: Center(
        child: Text(
          'RUN\nCREW',
          textAlign: TextAlign.center,
          style: TextStyle(
            color: AppColors.lime,
            fontSize: 40,
            height: 1.0,
            fontWeight: FontWeight.w800,
            letterSpacing: 2,
          ),
        ),
      ),
    );
  }
}
