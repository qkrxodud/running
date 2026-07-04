import 'package:dio/dio.dart';

import '../core/model/page_response.dart';
import '../core/model/race_dtos.dart';
import 'api_client.dart';

/// session-api.md v0.2 소비. 명령(open/register/start/cancel)은 **body 없음** —
/// 경로 + 토큰만. 모든 명령 응답은 SessionDetail(§3) shape.
abstract interface class SessionRepository {
  /// GET /crews/{crewId}/sessions — 세션 목록.
  Future<PageResponse<SessionSummary>> listByCrew(int crewId,
      {int page = 0, int size = 20});

  /// GET /sessions/{sessionId} — 세션 상세(참가자 포함).
  Future<SessionDetail> detail(int sessionId);

  /// POST /crews/{crewId}/sessions — 크루장 전용. 생성 시 status=DRAFT.
  Future<SessionDetail> create(int crewId, CreateSessionRequest request);

  /// POST /sessions/{id}/open — 크루장. DRAFT→OPEN(발행·참가 개방).
  Future<SessionDetail> open(int sessionId);

  /// POST /sessions/{id}/register — 멤버 opt-in 신청(OPEN 세션만, 멱등).
  Future<SessionDetail> register(int sessionId);

  /// POST /sessions/{id}/start — STARTED 신호(멱등, 선 register 필요).
  /// B2 는 서버 신호만 — 클라 트래킹 배선은 M2(D-1).
  Future<SessionDetail> start(int sessionId);

  /// POST /sessions/{id}/cancel — 크루장. DRAFT|OPEN|RUNNING→CANCELLED.
  Future<SessionDetail> cancel(int sessionId);
}

class HttpSessionRepository implements SessionRepository {
  HttpSessionRepository({required Dio dio}) : _dio = dio;

  final Dio _dio;

  @override
  Future<PageResponse<SessionSummary>> listByCrew(int crewId,
          {int page = 0, int size = 20}) =>
      _guard(() async {
        final r = await _dio.get<Map<String, dynamic>>(
          '/api/v1/crews/$crewId/sessions',
          queryParameters: {'page': page, 'size': size},
        );
        return PageResponse.fromJson(r.data!, SessionSummary.fromJson);
      });

  @override
  Future<SessionDetail> detail(int sessionId) => _guard(() async {
        final r = await _dio
            .get<Map<String, dynamic>>('/api/v1/sessions/$sessionId');
        return SessionDetail.fromJson(r.data!);
      });

  @override
  Future<SessionDetail> create(int crewId, CreateSessionRequest request) =>
      _guard(() async {
        final r = await _dio.post<Map<String, dynamic>>(
          '/api/v1/crews/$crewId/sessions',
          data: request.toJson(),
        );
        return SessionDetail.fromJson(r.data!);
      });

  @override
  Future<SessionDetail> open(int sessionId) => _command(sessionId, 'open');

  @override
  Future<SessionDetail> register(int sessionId) =>
      _command(sessionId, 'register');

  @override
  Future<SessionDetail> start(int sessionId) => _command(sessionId, 'start');

  @override
  Future<SessionDetail> cancel(int sessionId) => _command(sessionId, 'cancel');

  /// body 없는 명령 공통 — POST /sessions/{id}/{action}.
  Future<SessionDetail> _command(int sessionId, String action) =>
      _guard(() async {
        final r = await _dio.post<Map<String, dynamic>>(
          '/api/v1/sessions/$sessionId/$action',
        );
        return SessionDetail.fromJson(r.data!);
      });

  Future<T> _guard<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}
