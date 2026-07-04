import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';
import 'crew_providers.dart';

/// 크루 생성 (POST /crews). 성공 시 생성자가 LEADER — 상세로 이동.
class CrewCreateScreen extends ConsumerStatefulWidget {
  const CrewCreateScreen({super.key});

  @override
  ConsumerState<CrewCreateScreen> createState() => _CrewCreateScreenState();
}

class _CrewCreateScreenState extends ConsumerState<CrewCreateScreen> {
  final _nameController = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  /// 계약(crew-api.md §1): trim 후 1~50자.
  String? _validate(String raw) {
    final name = raw.trim();
    if (name.isEmpty) return '크루 이름을 입력해 주세요.';
    if (name.length > 50) return '크루 이름은 50자 이내여야 합니다.';
    return null;
  }

  Future<void> _submit() async {
    final err = _validate(_nameController.text);
    if (err != null) {
      setState(() => _error = err);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final crew =
          await ref.read(crewRepositoryProvider).create(_nameController.text);
      ref.invalidate(myCrewsProvider);
      if (!mounted) return;
      // 목록으로 돌아간 뒤 새 크루 상세로 진입.
      context.pop();
      context.push('/crews/${crew.id}');
    } on ApiException catch (e) {
      setState(() => _error = e.code == 'VALIDATION_ERROR'
          ? '크루 이름을 확인해 주세요.'
          : '크루 생성 실패: ${e.message}');
    } on Object {
      setState(() => _error = '서버에 연결할 수 없습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('크루 만들기')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 8),
              Container(
                width: 78,
                height: 78,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: AppColors.ink,
                  borderRadius: BorderRadius.circular(24),
                ),
                child: const Icon(Icons.groups, size: 38, color: AppColors.lime),
              ),
              const SizedBox(height: 24),
              const Text(
                '크루 이름',
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1,
                  color: Color(0xFF9AA18C),
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _nameController,
                autofocus: true,
                maxLength: 50,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _busy ? null : _submit(),
                decoration: const InputDecoration(
                  hintText: '예: 새벽 한강 크루',
                ),
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
                child: Text(_busy ? '생성 중…' : '크루 만들기'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
