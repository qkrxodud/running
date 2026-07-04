import 'package:flutter/material.dart';

import '../../app/app_theme.dart';

/// 강제 업데이트 차단 화면 (B1-C3). min_version 미충족 시 진행 차단.
/// 스토어 이동은 링크 발급물 대기 — 버튼은 안내 문구까지.
class ForceUpdateScreen extends StatelessWidget {
  const ForceUpdateScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.ink,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Icon(Icons.system_update, size: 72, color: AppColors.lime),
              const SizedBox(height: 24),
              const Text(
                '업데이트가 필요해요',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                '원활한 이용을 위해 최신 버전으로 업데이트해 주세요.',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.muted, fontSize: 15),
              ),
              const SizedBox(height: 32),
              FilledButton(
                // 스토어 딥링크는 배포 채널 확정 후 배선.
                onPressed: null,
                style: FilledButton.styleFrom(
                  disabledBackgroundColor: AppColors.lime,
                  disabledForegroundColor: AppColors.ink,
                ),
                child: const Text('스토어에서 업데이트 (준비 중)'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
