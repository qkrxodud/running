import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/app_theme.dart';
import '../../app/providers.dart';
import '../../core/model/api_error.dart';
import '../../core/model/race_dtos.dart';
import 'race_format.dart';
import 'race_providers.dart';

/// 세션 생성 (session-api.md §1) — 크루장 전용. 코스 선택 = GET courses(dev 시드)
/// 목록에서 고른다(지도 그리기 없이 성립 — §4). upload_deadline 기본값 +12h 는
/// 앱레이어 UX(수정 가능). 보상 텍스트는 M3(미포함).
class SessionCreateScreen extends ConsumerStatefulWidget {
  const SessionCreateScreen({super.key, required this.crewId});

  final int crewId;

  @override
  ConsumerState<SessionCreateScreen> createState() =>
      _SessionCreateScreenState();
}

class _SessionCreateScreenState extends ConsumerState<SessionCreateScreen> {
  int? _courseId;
  DateTime? _scheduledAt;
  DateTime? _uploadDeadline;
  bool _deadlineEdited = false;
  bool _busy = false;
  String? _error;

  /// scheduled 변경 시, 사용자가 직접 손대지 않았으면 마감을 +12h 로 따라가게.
  void _setScheduled(DateTime dt) {
    setState(() {
      _scheduledAt = dt;
      if (!_deadlineEdited) {
        _uploadDeadline = defaultUploadDeadline(dt);
      }
    });
  }

  Future<DateTime?> _pickDateTime(DateTime initial) async {
    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime.now().subtract(const Duration(days: 1)),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (date == null || !mounted) return null;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(initial),
    );
    if (time == null) return null;
    return DateTime(date.year, date.month, date.day, time.hour, time.minute);
  }

  String? _validate() {
    if (_courseId == null) return '코스를 선택해 주세요.';
    if (_scheduledAt == null) return '예정 일시를 선택해 주세요.';
    if (_uploadDeadline == null) return '업로드 마감을 선택해 주세요.';
    if (!_uploadDeadline!.isAfter(_scheduledAt!)) {
      return '업로드 마감은 예정 일시보다 뒤여야 합니다.';
    }
    return null;
  }

  Future<void> _submit() async {
    final err = _validate();
    if (err != null) {
      setState(() => _error = err);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final session = await ref.read(sessionRepositoryProvider).create(
            widget.crewId,
            CreateSessionRequest(
              courseId: _courseId!,
              scheduledAt: _scheduledAt!,
              uploadDeadline: _uploadDeadline!,
            ),
          );
      ref.invalidate(crewSessionsProvider(widget.crewId));
      if (!mounted) return;
      context.pop();
      context.push('/sessions/${session.id}');
    } on ApiException catch (e) {
      setState(() => _error = switch (e.code) {
            'VALIDATION_ERROR' => '입력 값을 확인해 주세요 (마감이 예정보다 앞설 수 없어요).',
            'FORBIDDEN' => '크루장만 세션을 만들 수 있어요.',
            'CREW_CLOSED' => '종료된 크루에서는 세션을 만들 수 없어요.',
            'SESSION_STATE_INVALID' => '선택한 코스가 이 크루의 코스가 아니에요.',
            _ => '세션 생성 실패: ${e.message}',
          });
    } on Object {
      setState(() => _error = '서버에 연결할 수 없습니다.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final courses = ref.watch(crewCoursesProvider(widget.crewId));
    return Scaffold(
      appBar: AppBar(title: const Text('세션 만들기')),
      backgroundColor: AppColors.bg,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
          children: [
            _sectionLabel('코스 선택'),
            const SizedBox(height: 8),
            courses.when(
              loading: () => const Padding(
                padding: EdgeInsets.all(24),
                child: Center(
                    child: CircularProgressIndicator(color: AppColors.ink)),
              ),
              error: (e, _) => _CoursesError(
                onRetry: () =>
                    ref.invalidate(crewCoursesProvider(widget.crewId)),
              ),
              data: (page) {
                if (page.items.isEmpty) return const _NoCourses();
                return Column(
                  children: [
                    for (final c in page.items)
                      _CourseTile(
                        course: c,
                        selected: _courseId == c.id,
                        onTap: () => setState(() => _courseId = c.id),
                      ),
                  ],
                );
              },
            ),
            const SizedBox(height: 24),
            _sectionLabel('예정 일시'),
            const SizedBox(height: 8),
            _DateTimeField(
              value: _scheduledAt,
              hint: '레이스 예정 일시 선택',
              onTap: () async {
                final dt = await _pickDateTime(
                    _scheduledAt ?? DateTime.now().add(const Duration(days: 1)));
                if (dt != null) _setScheduled(dt);
              },
            ),
            const SizedBox(height: 16),
            _sectionLabel('업로드 마감  ·  기본 +12h (수정 가능)'),
            const SizedBox(height: 8),
            _DateTimeField(
              value: _uploadDeadline,
              hint: '업로드 마감 일시',
              enabled: _scheduledAt != null,
              onTap: () async {
                final base = _uploadDeadline ??
                    defaultUploadDeadline(_scheduledAt ?? DateTime.now());
                final dt = await _pickDateTime(base);
                if (dt != null) {
                  setState(() {
                    _uploadDeadline = dt;
                    _deadlineEdited = true;
                  });
                }
              },
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(_error!,
                    style: const TextStyle(color: AppColors.accentPink)),
              ),
            const SizedBox(height: 28),
            FilledButton(
              onPressed: _busy ? null : _submit,
              child: Text(_busy ? '생성 중…' : '세션 만들기'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _sectionLabel(String text) => Text(
        text,
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w700,
          letterSpacing: 1,
          color: Color(0xFF9AA18C),
        ),
      );
}

class _CourseTile extends StatelessWidget {
  const _CourseTile({
    required this.course,
    required this.selected,
    required this.onTap,
  });

  final CourseSummary course;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Material(
        color: selected ? AppColors.ink : Colors.white,
        borderRadius: BorderRadius.circular(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Icon(Icons.route,
                    color: selected ? AppColors.lime : AppColors.ink),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        course.name,
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: selected ? Colors.white : AppColors.ink,
                        ),
                      ),
                      Text(
                        RaceFormat.distance(course.distanceM),
                        style: TextStyle(
                          fontSize: 13,
                          color: selected ? AppColors.muted : AppColors.mutedAlt,
                        ),
                      ),
                    ],
                  ),
                ),
                if (selected)
                  const Icon(Icons.check_circle, color: AppColors.lime),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DateTimeField extends StatelessWidget {
  const _DateTimeField({
    required this.value,
    required this.hint,
    required this.onTap,
    this.enabled = true,
  });

  final DateTime? value;
  final String hint;
  final VoidCallback onTap;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: enabled ? onTap : null,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Row(
            children: [
              Icon(Icons.event,
                  color: enabled ? AppColors.ink : AppColors.muted),
              const SizedBox(width: 12),
              Text(
                value == null ? hint : RaceFormat.dateTime(value!.toUtc()),
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: value == null ? FontWeight.w400 : FontWeight.w600,
                  color: value == null ? AppColors.muted : AppColors.ink,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NoCourses extends StatelessWidget {
  const _NoCourses();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
      ),
      child: const Column(
        children: [
          Icon(Icons.route_outlined, size: 40, color: AppColors.muted),
          SizedBox(height: 12),
          Text('선택할 코스가 없어요',
              style:
                  TextStyle(fontWeight: FontWeight.w800, color: AppColors.ink)),
          SizedBox(height: 6),
          Text(
            '코스 등록(지도에서 경로 그리기)은 준비 중이에요. '
            'dev 환경에서는 시드 코스가 제공됩니다.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.muted, fontSize: 13),
          ),
        ],
      ),
    );
  }
}

class _CoursesError extends StatelessWidget {
  const _CoursesError({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const Text('코스 목록을 불러오지 못했습니다.',
            style: TextStyle(color: AppColors.muted)),
        TextButton(onPressed: onRetry, child: const Text('다시 시도')),
      ],
    );
  }
}
