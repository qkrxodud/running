import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/app_config.dart';
import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';

/// 로그인 화면.
///
/// **카카오 로그인 교체 지점**: M0 카카오 앱 키 확보 후 kakao_flutter_sdk 버튼이
/// 이 화면의 본 CTA 가 된다(스텁 폼은 dev 플래그 뒤 유지). 스텁 규약은
/// auth-api.md §4 — `stub:{fake_kakao_id}`.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _idController = TextEditingController(text: 'dev-user-1');
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _idController.dispose();
    super.dispose();
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
              // 카카오 로그인 자리 — 앱 키 대기 (M0).
              FilledButton(
                onPressed: null,
                style: FilledButton.styleFrom(
                  disabledBackgroundColor: AppColors.ink,
                  disabledForegroundColor: AppColors.muted,
                  side: const BorderSide(color: AppColors.muted),
                ),
                child: const Text('카카오 로그인 (준비 중 — 키 발급 대기)'),
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
