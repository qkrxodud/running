/// 트랙 업로드/결과 오류의 **코드 기반 분기**(순수 Dart) — R-007 잔여 조건.
///
/// 계약 conventions §4: 클라 분기는 **code 로만** 한다(message 문자열 매칭 금지 —
/// 로케일 변동). track-api §1 평가 순서(404→**403**→409상태→409중복→400/413)에
/// 대응해 서버 오류를 클라 의미로 정규화한다:
///   - **403 FORBIDDEN** = 세션 소유 크루의 ACTIVE 멤버 아님(비멤버). ≠ 409.
///   - **409 SESSION_STATE_INVALID** = 크루 멤버지만 미등록(선 register) 또는 상태 부적합.
///   - **409 TRACK_ALREADY_UPLOADED** = 다른 내용 재업로드(멱등 위반).
/// 403/409 는 배타 — 같은 호출자에 동시 부여되지 않는다(track-api v0.1.1).
library;

import 'api_error.dart';

/// track-api 오류 code 상수(계약 §오류 코드).
class TrackErrorCodes {
  const TrackErrorCodes._();

  static const forbidden = 'FORBIDDEN';
  static const notFound = 'NOT_FOUND';
  static const sessionStateInvalid = 'SESSION_STATE_INVALID';
  static const alreadyUploaded = 'TRACK_ALREADY_UPLOADED';
  static const payloadInvalid = 'TRACK_PAYLOAD_INVALID';
  static const arrayLengthMismatch = 'TRACK_ARRAY_LENGTH_MISMATCH';
  static const tooLarge = 'TRACK_TOO_LARGE';
  static const validationError = 'VALIDATION_ERROR';
  static const resultNotReady = 'RESULT_NOT_READY';
}

/// 업로드 실패의 클라 의미 분류. UI 분기(재시도 가능/사용자 조치 필요/영구 실패)와
/// 업로드 큐 재시도 판단의 단일 근거.
enum TrackUploadErrorKind {
  /// 403 — 세션 소유 크루의 ACTIVE 멤버 아님(비멤버·탈퇴멤버). 재시도 무의미.
  forbiddenNotMember,

  /// 409 SESSION_STATE_INVALID — 미등록(선 register) 또는 상태 부적합.
  /// 사용자 조치(참가 신청) 후에나 가능 — 자동 재시도 무의미.
  notRegisteredOrBadState,

  /// 409 TRACK_ALREADY_UPLOADED — 다른 내용으로 이미 업로드됨(멱등 위반). 종단.
  alreadyUploaded,

  /// 400 계열(폴리라인 손상·배열 길이·필수필드/client_meta) — 클라 버그. 재시도 무의미.
  payloadInvalid,

  /// 413 — 크기 상한 초과. 재시도 무의미.
  tooLarge,

  /// 404 — 세션 없음. 재시도 무의미.
  notFound,

  /// 네트워크 단절(응답 없음, statusCode 0). **재시도 가능**.
  network,

  /// 5xx 서버 오류. **재시도 가능**.
  server,

  /// 미분류(계약 밖 code·예상 못 한 상태). 안전하게 종단 처리(로컬은 보존).
  unknown;

  /// 지수 백오프 자동 재시도 대상인가. 일시적 장애(네트워크·서버)만 true.
  /// 4xx 클라 오류는 재시도해도 동일 결과이므로 false(사용자 조치/수동 재시도로 회부).
  bool get isRetryable =>
      this == TrackUploadErrorKind.network ||
      this == TrackUploadErrorKind.server;
}

/// [ApiException] → [TrackUploadErrorKind]. **code 우선 분기**(메시지 매칭 없음).
TrackUploadErrorKind classifyTrackUploadError(ApiException e) {
  if (e.isNetworkError) return TrackUploadErrorKind.network;

  switch (e.code) {
    case TrackErrorCodes.forbidden:
      return TrackUploadErrorKind.forbiddenNotMember;
    case TrackErrorCodes.sessionStateInvalid:
      return TrackUploadErrorKind.notRegisteredOrBadState;
    case TrackErrorCodes.alreadyUploaded:
      return TrackUploadErrorKind.alreadyUploaded;
    case TrackErrorCodes.payloadInvalid:
    case TrackErrorCodes.arrayLengthMismatch:
    case TrackErrorCodes.validationError:
      return TrackUploadErrorKind.payloadInvalid;
    case TrackErrorCodes.tooLarge:
      return TrackUploadErrorKind.tooLarge;
    case TrackErrorCodes.notFound:
      return TrackUploadErrorKind.notFound;
  }

  // code 가 계약 밖이면 상태코드로 보강(403/404/413 은 code 누락에도 의미 확정).
  switch (e.statusCode) {
    case 403:
      return TrackUploadErrorKind.forbiddenNotMember;
    case 404:
      return TrackUploadErrorKind.notFound;
    case 413:
      return TrackUploadErrorKind.tooLarge;
  }
  if (e.statusCode >= 500) return TrackUploadErrorKind.server;
  return TrackUploadErrorKind.unknown;
}
