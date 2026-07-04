import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';
import '../../data/user_repository.dart';

/// 온보딩 닉네임 설정 (PUT /users/me/nickname). onboarding_completed=false 일 때 게이트.
/// 서버 컬럼(onboarded_at)이 진실 — 재설치·중단 후 재로그인에도 복원된다.
class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  final _nicknameController = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _nicknameController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final err = validateNickname(_nicknameController.text);
    if (err != null) {
      setState(() => _error = err);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(authControllerProvider.notifier)
          .setNickname(_nicknameController.text);
      // 상태가 authenticated 로 전이 → 라우터 redirect 가 홈으로 이동.
    } on ApiException catch (e) {
      setState(() => _error = e.code == 'VALIDATION_ERROR'
          ? '닉네임을 확인해 주세요 (1~30자).'
          : '설정 실패: ${e.message}');
    } on Object {
      setState(() => _error = '서버에 연결할 수 없습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 24),
              const Text(
                '어떻게 불러드릴까요?',
                style: TextStyle(
                  fontSize: 26,
                  fontWeight: FontWeight.w800,
                  color: AppColors.ink,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                '크루 안에서 표시될 닉네임을 정해주세요.',
                style: TextStyle(color: AppColors.muted, fontSize: 15),
              ),
              const SizedBox(height: 32),
              TextField(
                controller: _nicknameController,
                autofocus: true,
                maxLength: 30,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _busy ? null : _submit(),
                decoration: const InputDecoration(hintText: '닉네임 (1~30자)'),
              ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    _error!,
                    style: const TextStyle(color: AppColors.accentPink),
                  ),
                ),
              const Spacer(),
              FilledButton(
                onPressed: _busy ? null : _submit,
                child: Text(_busy ? '저장 중…' : '시작하기'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
