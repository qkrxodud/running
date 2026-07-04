import 'package:dio/dio.dart';

import '../core/model/api_error.dart';
import '../core/model/track_dtos.dart';
import '../core/model/track_error.dart';
import 'api_client.dart';

/// 결과 조회 결과 — 확정(ready) 또는 전원 완료 대기(pending).
///
/// `409 RESULT_NOT_READY` 는 예외가 아니라 **정상적인 "대기" 상태**로 매핑한다
/// (클라는 결과 대기 화면을 유지). track-api §3.
sealed class ResultQueryOutcome {
  const ResultQueryOutcome();
}

/// 결과 확정 — 순위표 표시.
class ResultReady extends ResultQueryOutcome {
  const ResultReady(this.result);
  final RaceResultResponse result;
}

/// 결과 미확정(RESULT_NOT_READY) — "전원 완료 대기" 화면 유지.
class ResultPending extends ResultQueryOutcome {
  const ResultPending();
}

/// track-api.md v0.1.1 소비. 오류는 계약 code 기반으로만 분기(메시지 매칭 금지).
///
/// - [upload] 은 `DioException` 을 [ApiException] 으로 정규화해 던진다(호출자·
///   [UploadCoordinator] 가 `classifyTrackUploadError` 로 403/409 분기).
/// - dio 의존은 이 data 계층에만 — core 는 순수 유지(R-002).
abstract interface class TrackRepository {
  /// POST /sessions/{id}/track — 완주 후 사후 업로드. 201(신규)/200(멱등 재요청).
  Future<TrackRecordResponse> upload(int sessionId, TrackUploadRequest request);

  /// GET /sessions/{id}/track/me — 내 업로드 상태. **미업로드(404) → null**.
  Future<TrackRecordResponse?> myTrack(int sessionId);

  /// GET /sessions/{id}/result — 결과·순위. 미확정 → [ResultPending].
  Future<ResultQueryOutcome> result(int sessionId);
}

class HttpTrackRepository implements TrackRepository {
  HttpTrackRepository({required Dio dio}) : _dio = dio;

  final Dio _dio;

  @override
  Future<TrackRecordResponse> upload(
    int sessionId,
    TrackUploadRequest request,
  ) =>
      _guard(() async {
        final r = await _dio.post<Map<String, dynamic>>(
          '/api/v1/sessions/$sessionId/track',
          data: request.toJson(),
        );
        // 201(신규)·200(동일 멱등 키 재요청) 모두 동일 shape.
        return TrackRecordResponse.fromJson(r.data!);
      });

  @override
  Future<TrackRecordResponse?> myTrack(int sessionId) async {
    try {
      final r = await _dio.get<Map<String, dynamic>>(
        '/api/v1/sessions/$sessionId/track/me',
      );
      return TrackRecordResponse.fromJson(r.data!);
    } on DioException catch (e) {
      final ex = toApiException(e);
      // 404 = 세션 없음 / 크루 멤버지만 내 트랙 미업로드 → "아직 업로드 없음".
      if (ex.statusCode == 404) return null;
      throw ex;
    }
  }

  @override
  Future<ResultQueryOutcome> result(int sessionId) async {
    try {
      final r = await _dio.get<Map<String, dynamic>>(
        '/api/v1/sessions/$sessionId/result',
      );
      return ResultReady(RaceResultResponse.fromJson(r.data!));
    } on DioException catch (e) {
      final ex = toApiException(e);
      // 409 RESULT_NOT_READY → 예외 아닌 "전원 완료 대기" 상태(code 로만 분기).
      if (ex.code == TrackErrorCodes.resultNotReady) {
        return const ResultPending();
      }
      throw ex;
    }
  }

  Future<T> _guard<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}
