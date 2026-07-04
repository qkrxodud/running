import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';
import '../../core/model/crew_dtos.dart';
import 'crew_providers.dart';

/// 초대 코드로 참가 (POST /crews/join). 코드 형식은 계약 문자 집합(6자, 혼동문자 제외).
class CrewJoinScreen extends ConsumerStatefulWidget {
  const CrewJoinScreen({super.key});

  @override
  ConsumerState<CrewJoinScreen> createState() => _CrewJoinScreenState();
}

class _CrewJoinScreenState extends ConsumerState<CrewJoinScreen> {
  final _codeController = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  /// 계약 오류 code → 사용자 문구 (message 매칭 금지, code 분기 — conventions §4).
  String _messageFor(ApiException e) {
    switch (e.code) {
      case 'INVITE_CODE_INVALID':
        return '존재하지 않는 초대 코드입니다.';
      case 'INVITE_CODE_EXPIRED':
        return '만료된 초대 코드입니다.';
      case 'INVITE_CODE_EXHAUSTED':
        return '사용 횟수가 모두 소진된 코드입니다.';
      case 'ALREADY_JOINED':
        return '이미 참가 중인 크루입니다.';
      case 'CREW_CLOSED':
        return '종료된 크루입니다.';
      case 'VALIDATION_ERROR':
        return '코드 형식을 확인해 주세요.';
      default:
        return '참가에 실패했습니다: ${e.message}';
    }
  }

  Future<void> _submit() async {
    final raw = _codeController.text;
    if (!InviteCodeFormat.isValid(raw)) {
      setState(() => _error = '초대 코드는 영문 대문자·숫자 6자입니다.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final crew = await ref.read(crewRepositoryProvider).join(raw);
      ref.invalidate(myCrewsProvider);
      if (!mounted) return;
      context.pop();
      context.push('/crews/${crew.id}');
    } on ApiException catch (e) {
      setState(() => _error = _messageFor(e));
    } on Object {
      setState(() => _error = '서버에 연결할 수 없습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('초대 코드로 참가')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 8),
              const Text(
                '크루장에게 받은 초대 코드를 입력하세요.',
                style: TextStyle(color: AppColors.muted, fontSize: 14),
              ),
              const SizedBox(height: 24),
              TextField(
                controller: _codeController,
                autofocus: true,
                maxLength: InviteCodeFormat.length,
                textCapitalization: TextCapitalization.characters,
                textAlign: TextAlign.center,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _busy ? null : _submit(),
                inputFormatters: [
                  // 허용 문자만 통과 + 대문자 정규화.
                  _UpperCaseFormatter(),
                  FilteringTextInputFormatter.allow(
                    RegExp('[${InviteCodeFormat.allowedChars}]'),
                  ),
                ],
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 8,
                  fontFeatures: [FontFeature.tabularFigures()],
                ),
                decoration: const InputDecoration(
                  hintText: 'K7QF2A',
                  counterText: '',
                ),
              ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(
                    _error!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: AppColors.accentPink),
                  ),
                ),
              const Spacer(),
              FilledButton(
                onPressed: _busy ? null : _submit,
                child: Text(_busy ? '참가 중…' : '크루 참가'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _UpperCaseFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    return newValue.copyWith(text: newValue.text.toUpperCase());
  }
}
