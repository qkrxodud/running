import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/app_config.dart';
import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';

/// 로그인 화면.
///
/// **본 CTA = 카카오 로그인** (앱 키 주입 시 활성 — `kakaoLoginReadyProvider`).
/// 카카오 SDK 로 access token 을 얻어 서버 `POST /auth/login` 으로 전달(경로는
/// 스텁과 동일, 분기 없음 — auth-api.md §1). 키 미주입 빌드는 "준비 중" 비활성.
/// dev 스텁 폼(`stub:{fake_kakao_id}`, auth-api.md §4)은 비prod 에서 그대로 유지.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _idController = TextEditingController(text: 'dev-user-1');
  bool _busy = false;
  bool _kakaoBusy = false;
  String? _error;

  @override
  void dispose() {
    _idController.dispose();
    super.dispose();
  }

  Future<void> _loginKakao() async {
    setState(() {
      _kakaoBusy = true;
      _error = null;
    });
    try {
      final token = await ref.read(kakaoAuthServiceProvider).login();
      // 취소(null)는 조용히 종결 — 오류 아님.
      if (token == null) return;
      await ref.read(authControllerProvider.notifier).loginWithKakao(token);
      // 라우터 redirect 가 상태 변화를 보고 이동시킨다.
    } on ApiException catch (e) {
      setState(() => _error = e.code == AuthErrorCodes.kakaoTokenInvalid
          ? '카카오 인증에 실패했습니다. 다시 시도해 주세요.'
          : '로그인 실패: ${e.code}');
    } on Object {
      setState(() => _error = '카카오 로그인 중 문제가 발생했습니다.');
    } finally {
      if (mounted) setState(() => _kakaoBusy = false);
    }
  }

  Future<void> _loginStub() async {
    final fakeId = _idController.text.trim();
    if (fakeId.isEmpty) {
      setState(() => _error = '개발용 아이디를 입력해 주세요.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).loginWithStub(fakeId);
      // 라우터 redirect 가 상태 변화를 보고 이동시킨다.
    } on ApiException catch (e) {
      setState(() => _error = e.code == AuthErrorCodes.kakaoTokenInvalid
          ? '로그인에 실패했습니다 (스텁 검증 거부).'
          : '로그인 실패: ${e.code}');
    } on Object {
      setState(() => _error = '서버에 연결할 수 없습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.ink,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Spacer(),
              const Text(
                'RUN\nCREW',
                style: TextStyle(
                  color: AppColors.lime,
                  fontSize: 56,
                  height: 1.0,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 2,
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                '크루와 함께 달리고, 기록으로 겨루세요.',
                style: TextStyle(color: AppColors.muted, fontSize: 15),
              ),
              const Spacer(),
              _KakaoLoginButton(
                ready: ref.watch(kakaoLoginReadyProvider),
                busy: _kakaoBusy,
                onPressed: _loginKakao,
              ),
              // dev·sandbox 에서만 노출(prod 차단). 게이트 단일 창구 = AppConfig.
              if (AppConfig.devLoginEnabled) ...[
                const SizedBox(height: 24),
                const Text(
                  'DEV 로그인 (스텁 — prod 미포함)',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _idController,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    hintText: 'fake_kakao_id (예: dev-user-1)',
                    fillColor: Color(0xFF23271C),
                  ),
                ),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: _busy ? null : _loginStub,
                  child: Text(_busy ? '로그인 중…' : '개발용 로그인'),
                ),
              ],
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(
                    _error!,
                    style: const TextStyle(color: AppColors.accentPink),
                  ),
                ),
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }
}

/// 카카오 로그인 CTA. [ready]=false(키 미주입)면 "준비 중" 비활성으로 둔다.
class _KakaoLoginButton extends StatelessWidget {
  const _KakaoLoginButton({
    required this.ready,
    required this.busy,
    required this.onPressed,
  });

  final bool ready;
  final bool busy;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    if (!ready) {
      return FilledButton(
        onPressed: null,
        style: FilledButton.styleFrom(
          disabledBackgroundColor: AppColors.ink,
          disabledForegroundColor: AppColors.muted,
          side: const BorderSide(color: AppColors.muted),
        ),
        child: const Text('카카오 로그인 (준비 중 — 키 발급 대기)'),
      );
    }
    return FilledButton(
      onPressed: busy ? null : onPressed,
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.lime,
        foregroundColor: AppColors.ink,
        minimumSize: const Size.fromHeight(52),
      ),
      child: Text(busy ? '로그인 중…' : '카카오로 시작하기'),
    );
  }
}
