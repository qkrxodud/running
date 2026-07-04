import 'package:dio/dio.dart';

import '../core/model/app_version_dtos.dart';
import '../core/model/user_dtos.dart' show AppPlatform;

/// app-version.md 소비 (인증 불요 — 로그인보다 앞서 호출).
abstract interface class AppVersionRepository {
  /// GET /app-version?platform=… — 실패(네트워크·404·파싱)는 전부 null.
  ///
  /// null = "판단 불가" → 강제 업데이트 아님 (가용성 우선: 서버 다운이
  /// 앱 사용을 막지 않는다 — 계약 §오류 제안·B1-C3 AC).
  Future<AppVersionInfo?> fetch(AppPlatform platform);
}

class HttpAppVersionRepository implements AppVersionRepository {
  HttpAppVersionRepository({required Dio dio}) : _dio = dio;

  final Dio _dio;

  @override
  Future<AppVersionInfo?> fetch(AppPlatform platform) async {
    try {
      final r = await _dio.get<Map<String, dynamic>>(
        '/api/v1/app-version',
        queryParameters: {'platform': platform.toWire()},
      );
      final data = r.data;
      if (data == null) return null;
      return AppVersionInfo.fromJson(data);
    } on DioException {
      return null; // 404(미설정)·네트워크 오류 — 게이트 통과
    } on Object {
      return null; // 파싱 실패도 통과 (게이트가 앱을 잠그면 안 됨)
    }
  }
}
