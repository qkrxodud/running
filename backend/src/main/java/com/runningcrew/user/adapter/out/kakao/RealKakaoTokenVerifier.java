package com.runningcrew.user.adapter.out.kakao;

import com.runningcrew.user.application.port.out.KakaoTokenInvalidException;
import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import com.runningcrew.user.application.port.out.KakaoUnavailableException;
import com.runningcrew.user.domain.KakaoAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 실 카카오 토큰 검증 어댑터 — {@code GET https://kapi.kakao.com/v2/user/me}를 사용자 액세스 토큰(Bearer)으로
 * 호출해 카카오 회원번호({@code id})를 얻어 {@link KakaoAccount}로 봉인한다(설계 12 §2, 계약 auth-api.md §1).
 *
 * <p><b>앱 키 불필요</b>: user/me는 사용자 토큰만으로 호출된다(Admin/REST 앱 키 미사용). 따라서 서버에 카카오
 * 앱 키 설정이 없다 — base-url·타임아웃만 외부화({@link KakaoProperties}).
 *
 * <p>오류 매핑:
 * <ul>
 *   <li>kapi 401/400(만료·위조·형식 오류) → {@link KakaoTokenInvalidException} → 어댑터 경계에서
 *       {@code AUTH_KAKAO_TOKEN_INVALID}(기존 경로, AuthService).
 *   <li>kapi 5xx·연결 실패·타임아웃(연결 3s/응답 5s) → 토큰 문제가 아닌 상위 장애. 계약(auth-api.md §1)이
 *       login에 허용하는 오류는 {@code AUTH_KAKAO_TOKEN_INVALID}·{@code VALIDATION_ERROR}뿐이므로, 계약을
 *       임의 확장하지 않고 동일 코드로 매핑하되 WARN 로그로 원인을 구분 기록한다(장애 전용 502 코드는 보류 —
 *       리포트 참조, domain-analyst 계약 추가 대상).
 * </ul>
 */
public class RealKakaoTokenVerifier implements KakaoTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(RealKakaoTokenVerifier.class);
    private static final String USER_ME_PATH = "/v2/user/me";

    private final RestClient restClient;

    public RealKakaoTokenVerifier(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public KakaoAccount verify(String kakaoAccessToken) {
        if (kakaoAccessToken == null || kakaoAccessToken.isBlank()) {
            throw new KakaoTokenInvalidException("카카오 액세스 토큰이 비어 있습니다.");
        }

        KakaoUserResponse body;
        try {
            body = restClient.get()
                    .uri(USER_ME_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 400) {
                // 카카오가 토큰을 명확히 거부(만료·위조·형식 오류).
                throw new KakaoTokenInvalidException("카카오 토큰 검증에 실패했습니다(kapi " + status + ").");
            }
            // 5xx 등 — 토큰 문제 아님(상위 장애) → 503 AUTH_KAKAO_UNAVAILABLE(계약 auth-api §1 v0.1.1).
            log.warn("카카오 user/me 비정상 응답: status={} body={}", status, e.getResponseBodyAsString());
            throw new KakaoUnavailableException("카카오 인증 서버 오류(kapi " + status + ").");
        } catch (ResourceAccessException e) {
            // 연결 거부·타임아웃(연결 3s/응답 5s 초과) — 상위 장애 → 503(재시도 대상, 재로그인 아님).
            log.warn("카카오 user/me 접속 실패(타임아웃/연결): {}", e.getMessage());
            throw new KakaoUnavailableException("카카오 인증 서버에 접속하지 못했습니다.");
        }

        if (body == null || body.id() == null) {
            throw new KakaoTokenInvalidException("카카오 응답에 회원번호(id)가 없습니다.");
        }
        return new KakaoAccount(String.valueOf(body.id()));
    }
}
