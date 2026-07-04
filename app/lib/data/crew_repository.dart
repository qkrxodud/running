import 'package:dio/dio.dart';

import '../core/model/crew_dtos.dart';
import '../core/model/page_response.dart';
import 'api_client.dart';

/// crew-api.md v0.2 소비.
abstract interface class CrewRepository {
  /// GET /crews — 내 크루 목록 (페이지네이션).
  Future<PageResponse<CrewSummary>> myCrews({int page = 0, int size = 20});

  /// POST /crews — 크루 생성 (생성자가 LEADER).
  Future<CrewDetail> create(String name);

  /// GET /crews/{crewId} — 크루 상세 (멤버 목록 — O-1 인앱 갈음 지점).
  Future<CrewDetail> detail(int crewId);

  /// POST /crews/{crewId}/invite-codes — 크루장 전용.
  Future<InviteCodeInfo> createInviteCode(
    int crewId, {
    required int maxUses,
    required int expiresInHours,
  });

  /// POST /crews/join — 초대 코드로 참가.
  Future<CrewDetail> join(String code);
}

class HttpCrewRepository implements CrewRepository {
  HttpCrewRepository({required Dio dio}) : _dio = dio;

  final Dio _dio;

  @override
  Future<PageResponse<CrewSummary>> myCrews({int page = 0, int size = 20}) =>
      _guard(() async {
        final r = await _dio.get<Map<String, dynamic>>(
          '/api/v1/crews',
          queryParameters: {'page': page, 'size': size},
        );
        return PageResponse.fromJson(r.data!, CrewSummary.fromJson);
      });

  @override
  Future<CrewDetail> create(String name) => _guard(() async {
        final r = await _dio.post<Map<String, dynamic>>(
          '/api/v1/crews',
          data: {'name': name},
        );
        return CrewDetail.fromJson(r.data!);
      });

  @override
  Future<CrewDetail> detail(int crewId) => _guard(() async {
        final r =
            await _dio.get<Map<String, dynamic>>('/api/v1/crews/$crewId');
        return CrewDetail.fromJson(r.data!);
      });

  @override
  Future<InviteCodeInfo> createInviteCode(
    int crewId, {
    required int maxUses,
    required int expiresInHours,
  }) =>
      _guard(() async {
        final r = await _dio.post<Map<String, dynamic>>(
          '/api/v1/crews/$crewId/invite-codes',
          data: {'max_uses': maxUses, 'expires_in_hours': expiresInHours},
        );
        return InviteCodeInfo.fromJson(r.data!);
      });

  @override
  Future<CrewDetail> join(String code) => _guard(() async {
        final r = await _dio.post<Map<String, dynamic>>(
          '/api/v1/crews/join',
          data: {'code': InviteCodeFormat.normalize(code)},
        );
        return CrewDetail.fromJson(r.data!);
      });

  Future<T> _guard<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

/// 초대코드 유효기간 UX 기본값 72h — 도메인 규칙 아님(crew-api.md §4), 입력 폼 프리필.
const int inviteCodeDefaultExpiresHours = 72;

/// 초대코드 최대 사용횟수 UX 기본값.
const int inviteCodeDefaultMaxUses = 10;
