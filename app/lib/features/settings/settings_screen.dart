import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';
import '../../data/user_repository.dart';

/// 방침·약관 URL — 도메인(Pages) 발급물 대기. placeholder 상수로 격리(B1-C4).
const String kPrivacyPolicyUrl = 'https://example.com/privacy'; // TODO(도메인 대기)
const String kTermsOfServiceUrl = 'https://example.com/terms'; // TODO(도메인 대기)

/// 설정: 닉네임 수정 · 로그아웃 · 회원 탈퇴(2단 확인, Play 계정 삭제 요건 진입점).
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(authControllerProvider).profile;
    return Scaffold(
      appBar: AppBar(title: const Text('설정')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            // 프로필 요약
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Row(
                children: [
                  const CircleAvatar(
                    radius: 26,
                    backgroundColor: AppColors.ink,
                    child: Icon(Icons.person, color: AppColors.lime),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Text(
                      profile?.nickname ?? '러너',
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        color: AppColors.ink,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: () => _editNickname(context, ref),
                    child: const Text('닉네임 수정'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            _tile(
              icon: Icons.leaderboard_outlined,
              label: '내 기록 · 개인 최고',
              onTap: () => context.push('/history'),
            ),
            const SizedBox(height: 16),
            _tile(
              icon: Icons.description_outlined,
              label: '이용약관',
              // 링크 열기는 도메인 URL 확정 후 배선(url_launcher 대기).
              onTap: () => _notReady(context, '약관 페이지는 준비 중입니다.'),
            ),
            _tile(
              icon: Icons.privacy_tip_outlined,
              label: '개인정보 처리방침',
              onTap: () => _notReady(context, '방침 페이지는 준비 중입니다.'),
            ),
            if (kDebugMode)
              _tile(
                icon: Icons.bug_report_outlined,
                label: '트래킹 스파이크 (개발용)',
                onTap: () => context.push('/spike'),
              ),
            const SizedBox(height: 16),
            _tile(
              icon: Icons.logout,
              label: '로그아웃',
              onTap: () => _logout(context, ref),
            ),
            _tile(
              icon: Icons.person_remove_outlined,
              label: '회원 탈퇴',
              danger: true,
              onTap: () => _withdraw(context, ref),
            ),
          ],
        ),
      ),
    );
  }

  Widget _tile({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    bool danger = false,
  }) {
    final color = danger ? AppColors.accentPink : AppColors.ink;
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      leading: Icon(icon, color: color),
      title: Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w600)),
      trailing: const Icon(Icons.chevron_right, color: AppColors.muted),
      onTap: onTap,
    );
  }

  void _notReady(BuildContext context, String msg) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _editNickname(BuildContext context, WidgetRef ref) async {
    final controller = TextEditingController(
      text: ref.read(authControllerProvider).profile?.nickname ?? '',
    );
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('닉네임 수정'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 30,
          decoration: const InputDecoration(hintText: '닉네임 (1~30자)'),
        ),
        actions: [
          TextButton(onPressed: () => ctx.pop(), child: const Text('취소')),
          FilledButton(
            onPressed: () => ctx.pop(controller.text),
            child: const Text('저장'),
          ),
        ],
      ),
    );
    if (result == null) return;
    final err = validateNickname(result);
    if (err != null) {
      if (context.mounted) _notReady(context, err);
      return;
    }
    try {
      await ref.read(authControllerProvider.notifier).setNickname(result);
      if (context.mounted) _notReady(context, '닉네임을 변경했습니다.');
    } on ApiException catch (e) {
      if (context.mounted) _notReady(context, '변경 실패: ${e.message}');
    } on Object {
      if (context.mounted) _notReady(context, '서버에 연결할 수 없습니다.');
    }
  }

  Future<void> _logout(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('로그아웃'),
        content: const Text('로그아웃하시겠어요?'),
        actions: [
          TextButton(onPressed: () => ctx.pop(false), child: const Text('취소')),
          FilledButton(
            onPressed: () => ctx.pop(true),
            child: const Text('로그아웃'),
          ),
        ],
      ),
    );
    if (ok == true) {
      await ref.read(authControllerProvider.notifier).logout();
    }
  }

  /// 회원 탈퇴 — 2단 확인. 계약(user-api.md §3): 재로그인 시 신규 계정·복구 불가 고지 필수.
  Future<void> _withdraw(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('정말 탈퇴하시겠어요?'),
        content: const Text(
          '탈퇴하면 닉네임·계정 정보와 개인 트랙 기록이 즉시 삭제됩니다.\n\n'
          '같은 카카오 계정으로 다시 로그인해도 새 계정으로 시작되며, '
          '과거 기록·개인 기록(PB)은 복구할 수 없습니다.',
        ),
        actions: [
          TextButton(onPressed: () => ctx.pop(false), child: const Text('취소')),
          TextButton(
            onPressed: () => ctx.pop(true),
            style: TextButton.styleFrom(foregroundColor: AppColors.accentPink),
            child: const Text('계속'),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;

    // 2단계: 최종 확인.
    final finalOk = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('최종 확인'),
        content: const Text('이 작업은 되돌릴 수 없습니다. 탈퇴를 진행할까요?'),
        actions: [
          TextButton(onPressed: () => ctx.pop(false), child: const Text('취소')),
          FilledButton(
            onPressed: () => ctx.pop(true),
            style: FilledButton.styleFrom(backgroundColor: AppColors.accentPink),
            child: const Text('탈퇴하기'),
          ),
        ],
      ),
    );
    if (finalOk != true) return;
    try {
      await ref.read(authControllerProvider.notifier).withdraw();
      // 상태 loggedOut 전이 → 라우터가 로그인 화면으로.
    } on ApiException catch (e) {
      if (context.mounted) _notReady(context, '탈퇴 실패: ${e.message}');
    } on Object {
      if (context.mounted) _notReady(context, '서버에 연결할 수 없습니다.');
    }
  }
}
