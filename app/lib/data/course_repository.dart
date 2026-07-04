import 'package:dio/dio.dart';

import '../core/model/page_response.dart';
import '../core/model/race_dtos.dart';
import 'api_client.dart';

/// course-api.md v0.1 소비. 코스는 불변 애그리거트 — 생성·조회만(수정/삭제 없음).
abstract interface class CourseRepository {
  /// GET /crews/{crewId}/courses — 코스 목록(세션 생성 UI 의 선택 소스).
  Future<PageResponse<CourseSummary>> listByCrew(int crewId,
      {int page = 0, int size = 20});

  /// GET /courses/{courseId} — 코스 상세(폴리라인 포함, 미리보기용).
  Future<CourseDetail> detail(int courseId);

  /// POST /crews/{crewId}/courses — 크루장 전용. distance_m 은 서버 확정.
  /// 지도 그리기 UI 는 대기(Client ID) — B2 는 인코딩 폴리라인 직수신 경로만.
  Future<CourseDetail> create(
    int crewId, {
    required String name,
    required String routePolyline,
    required double startLat,
    required double startLng,
    required double finishLat,
    required double finishLng,
  });
}

class HttpCourseRepository implements CourseRepository {
  HttpCourseRepository({required Dio dio}) : _dio = dio;

  final Dio _dio;

  @override
  Future<PageResponse<CourseSummary>> listByCrew(int crewId,
          {int page = 0, int size = 20}) =>
      _guard(() async {
        final r = await _dio.get<Map<String, dynamic>>(
          '/api/v1/crews/$crewId/courses',
          queryParameters: {'page': page, 'size': size},
        );
        return PageResponse.fromJson(r.data!, CourseSummary.fromJson);
      });

  @override
  Future<CourseDetail> detail(int courseId) => _guard(() async {
        final r = await _dio
            .get<Map<String, dynamic>>('/api/v1/courses/$courseId');
        return CourseDetail.fromJson(r.data!);
      });

  @override
  Future<CourseDetail> create(
    int crewId, {
    required String name,
    required String routePolyline,
    required double startLat,
    required double startLng,
    required double finishLat,
    required double finishLng,
  }) =>
      _guard(() async {
        final r = await _dio.post<Map<String, dynamic>>(
          '/api/v1/crews/$crewId/courses',
          data: {
            'name': name,
            'route_polyline': routePolyline,
            'start_lat': startLat,
            'start_lng': startLng,
            'finish_lat': finishLat,
            'finish_lng': finishLng,
          },
        );
        return CourseDetail.fromJson(r.data!);
      });

  Future<T> _guard<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}
