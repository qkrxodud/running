import 'package:dio/dio.dart';

import '../core/model/replay_dtos.dart';
import 'api_client.dart';

/// replay-api.md v0.1 소비. 크루 멤버 조회(비멤버 403). status 별 응답 분기는
/// [ReplaySnapshotResponse] 가 담는다(READY 만 payload).
abstract interface class ReplayRepository {
  /// GET /sessions/{id}/replay — 최신 스냅샷(재생성 시 created_at max).
  Future<ReplaySnapshotResponse> snapshot(int sessionId);
}

class HttpReplayRepository implements ReplayRepository {
  HttpReplayRepository({required Dio dio}) : _dio = dio;

  final Dio _dio;

  @override
  Future<ReplaySnapshotResponse> snapshot(int sessionId) async {
    try {
      final r = await _dio.get<Map<String, dynamic>>(
        '/api/v1/sessions/$sessionId/replay',
      );
      return ReplaySnapshotResponse.fromJson(r.data!);
    } on DioException catch (e) {
      // 404(세션 없음/스냅샷 미생성)·403(비멤버) 는 그대로 정규화해 화면이 분기.
      throw toApiException(e);
    }
  }
}
