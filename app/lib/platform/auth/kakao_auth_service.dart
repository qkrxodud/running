import 'package:flutter/services.dart';
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart';

/// 카카오 로그인 경계 — 카카오 SDK import 를 이 파일 안으로만 격리한다.
///
/// 로그인 UI/컨트롤러는 이 추상에만 의존하므로, SDK 교체·iOS 확장·위젯 테스트가
/// 상위 레이어를 건드리지 않는다(플랫폼 격리 원칙). 반환값 계약:
///   - 성공 → 카카오 **access token** 문자열 (서버 `POST /auth/login` 의
///     `kakao_access_token` 으로 그대로 전달 — auth-api.md §1).
///   - 사용자 취소 → `null` (호출자는 조용히 무시).
///   - 그 외 실패 → 예외 전파 (호출자가 오류 표시).
abstract interface class KakaoAuthService {
  Future<String?> login();
}

/// 카카오 SDK 초기화 — main 부트스트랩에서 1회 호출.
///
/// 키가 비어있으면 init 을 생략한다(AppConfig 게이트 유지) — 키 미주입 빌드는
/// 카카오 버튼 자체가 비활성이므로 SDK 를 건드릴 일이 없다.
void initKakaoSdk(String nativeAppKey) {
  if (nativeAppKey.isEmpty) return;
  KakaoSdk.init(nativeAppKey: nativeAppKey);
}

/// 카카오 공식 SDK(`kakao_flutter_sdk_user`) 구현.
///
/// 표준 패턴: 카카오톡 설치 시 톡 로그인, 미설치·톡 오류 시 카카오계정 로그인 폴백.
/// 톡 로그인에서 사용자가 취소하면 폴백하지 않고 취소로 종결한다(의도 존중).
class KakaoSdkAuthService implements KakaoAuthService {
  const KakaoSdkAuthService();

  @override
  Future<String?> login() async {
    try {
      final OAuthToken token = await isKakaoTalkInstalled()
          ? await _loginWithTalkOrAccountFallback()
          : await UserApi.instance.loginWithKakaoAccount();
      return token.accessToken;
    } on Object catch (error) {
      if (_isCancellation(error)) return null;
      rethrow;
    }
  }

  Future<OAuthToken> _loginWithTalkOrAccountFallback() async {
    try {
      return await UserApi.instance.loginWithKakaoTalk();
    } on Object catch (error) {
      // 취소는 폴백하지 않고 상위로 올려 조용히 종결(사용자 의도 존중).
      if (_isCancellation(error)) rethrow;
      // 톡에 계정 미로그인 등 → 카카오계정 로그인으로 폴백.
      return UserApi.instance.loginWithKakaoAccount();
    }
  }

  /// 취소 신호는 SDK 경로에 따라 세 형태로 온다:
  ///  - 톡 네이티브 취소 → [PlatformException] `code == 'CANCELED'`
  ///  - SDK 클라이언트 취소(선택창 닫힘 등) → [KakaoClientException] `cancelled`
  ///  - 계정 웹 로그인 거부 → [KakaoAuthException] `accessDenied`
  static bool _isCancellation(Object error) {
    if (error is PlatformException) return error.code == 'CANCELED';
    if (error is KakaoClientException) {
      return error.reason == ClientErrorCause.cancelled;
    }
    if (error is KakaoAuthException) {
      return error.error == AuthErrorCause.accessDenied;
    }
    return false;
  }
}
