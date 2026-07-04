import 'package:dio/dio.dart';

import '../core/model/history_dtos.dart';
import '../core/model/page_response.dart';
import 'api_client.dart';

/// history-api.md v0.1 소비. 본인 한정(토큰 sub). 페이지네이션 래퍼.
abstract interface class HistoryRepository {
  /// GET /me/records — 내 기록 히스토리(완주·DNF, 최신순).
  Future<PageResponse<RecordHistoryItem>> myRecords({int page = 0, int size = 20});

  /// GET /me/personal-bests — 내 코스별 개인 최고기록(완주만).
  Future<PageResponse<PersonalBestItem>> myPersonalBests(
      {int page = 0, int size = 20});
}

class HttpHistoryRepository implements HistoryRepository {
  HttpHistoryRepository({required Dio dio}) : _dio = dio;

  final Dio _dio;

  @override
  Future<PageResponse<RecordHistoryItem>> myRecords(
          {int page = 0, int size = 20}) =>
      _guard(() async {
        final r = await _dio.get<Map<String, dynamic>>(
          '/api/v1/me/records',
          queryParameters: {'page': page, 'size': size},
        );
        return PageResponse.fromJson(r.data!, RecordHistoryItem.fromJson);
      });

  @override
  Future<PageResponse<PersonalBestItem>> myPersonalBests(
          {int page = 0, int size = 20}) =>
      _guard(() async {
        final r = await _dio.get<Map<String, dynamic>>(
          '/api/v1/me/personal-bests',
          queryParameters: {'page': page, 'size': size},
        );
        return PageResponse.fromJson(r.data!, PersonalBestItem.fromJson);
      });

  Future<T> _guard<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}
