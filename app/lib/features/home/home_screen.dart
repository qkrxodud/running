import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';

/// 홈 셸(뼈대). 카운터 스캐폴드를 대체하는 빈 홈 — 실제 크루 홈 UI는
/// 1a 라임 디자인 기준으로 배치 B에서 구현한다.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('러닝크루'),
        backgroundColor: AppColors.ink,
        foregroundColor: AppColors.lime,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text(
              '러닝크루',
              style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            const Text(
              '화면 뼈대는 배치 B에서 구현됩니다.',
              style: TextStyle(color: AppColors.muted),
            ),
            const SizedBox(height: 24),
            // 실기기 트래킹 스파이크 검증 대기 중이므로 진입점 유지.
            OutlinedButton(
              onPressed: () => context.go('/spike'),
              child: const Text('트래킹 스파이크 (개발용)'),
            ),
          ],
        ),
      ),
    );
  }
}
