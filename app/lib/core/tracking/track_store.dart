import 'dart:convert';
import 'dart:io';

import 'track_point.dart';

/// 로컬 우선 트랙 저장소 — JSON Lines 파일에 포인트를 즉시 append 한다.
///
/// "서버 다운 = 결과 지연이지 유실이 아니다" 원칙의 스파이크 구현:
/// 포인트는 수신 즉시 디스크에 기록되고, 프로세스가 강제 종료돼도
/// 마지막 flush 까지의 데이터는 생존한다. 업로드 성공 확인 전에는
/// 어떤 경로로도 삭제하지 않는다.
class TrackStore {
  TrackStore(this._file);

  final File _file;

  /// 세션 파일 경로 규칙: {dir}/spike_track_{yyyyMMdd_HHmmss}.jsonl
  factory TrackStore.forNewSession(Directory dir, DateTime startedAt) {
    final ts = startedAt
        .toIso8601String()
        .replaceAll(RegExp(r'[-:]'), '')
        .split('.')
        .first
        .replaceAll('T', '_');
    return TrackStore(File('${dir.path}/spike_track_$ts.jsonl'));
  }

  String get path => _file.path;

  Future<void> append(TrackPoint point) async {
    await _file.writeAsString(
      '${jsonEncode(point.toJson())}\n',
      mode: FileMode.append,
      flush: true, // 스파이크에선 유실 측정이 목적이므로 매 포인트 flush
    );
  }

  Future<List<TrackPoint>> readAll() async {
    if (!await _file.exists()) return const [];
    final lines = await _file.readAsLines();
    return [
      for (final line in lines)
        if (line.trim().isNotEmpty)
          TrackPoint.fromJson(jsonDecode(line) as Map<String, dynamic>),
    ];
  }

  /// 세션 디렉토리의 모든 스파이크 트랙 파일 (검증·분석용)
  static Future<List<File>> listSessionFiles(Directory dir) async {
    if (!await dir.exists()) return const [];
    final files = await dir
        .list()
        .where((e) => e is File && e.path.contains('spike_track_'))
        .cast<File>()
        .toList();
    files.sort((a, b) => a.path.compareTo(b.path));
    return files;
  }
}
