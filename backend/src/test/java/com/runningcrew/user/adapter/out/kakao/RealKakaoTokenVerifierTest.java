package com.runningcrew.user.adapter.out.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runningcrew.user.application.port.out.KakaoTokenInvalidException;
import com.runningcrew.user.application.port.out.KakaoUnavailableException;
import com.runningcrew.user.domain.KakaoAccount;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RealKakaoTokenVerifier 통합 테스트 — JDK 내장 {@link HttpServer}로 kapi {@code /v2/user/me}를 목킹.
 *
 * <p>실 HTTP 왕복 + 실 타임아웃까지 검증한다(MockRestServiceServer는 소켓·타임아웃을 흉내내지 못함).
 * 외부 의존(MockWebServer/WireMock) 추가 없이 표준 라이브러리만 사용.
 * <ul>
 *   <li>정상 200 {@code {"id":...}} → KakaoAccount(회원번호 문자열) + Authorization Bearer 헤더 전달 확인
 *   <li>401 → KakaoTokenInvalidException(만료·위조)
 *   <li>응답 지연(read timeout 초과) → KakaoTokenInvalidException(장애 경로, WARN)
 *   <li>5xx → KakaoTokenInvalidException(장애 경로) / id 누락 200 → KakaoTokenInvalidException
 * </ul>
 */
class RealKakaoTokenVerifierTest {

    private HttpServer server;
    private volatile String capturedAuthHeader;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private RealKakaoTokenVerifier verifierWithTimeouts(Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(read);
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .requestFactory(factory)
                .build();
        return new RealKakaoTokenVerifier(client);
    }

    /** {@code /v2/user/me} 핸들러를 등록하고 서버를 기동. body=null이면 응답 본문 생략. */
    private void startServer(int status, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/user/me", exchange -> {
            capturedAuthHeader = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
    }

    @Test
    void 정상_응답이면_회원번호를_KakaoAccount로_추출하고_Bearer를_전달한다() throws IOException {
        startServer(200, "{\"id\": 4224881234, \"connected_at\": \"2026-01-01T00:00:00Z\"}", 0);

        KakaoAccount account = verifierWithTimeouts(Duration.ofSeconds(2)).verify("real-kakao-access-token");

        assertThat(account.kakaoId()).isEqualTo("4224881234");
        assertThat(capturedAuthHeader).isEqualTo("Bearer real-kakao-access-token");
    }

    @Test
    void kapi_401이면_KakaoTokenInvalidException() throws IOException {
        startServer(401, "{\"code\": -401, \"msg\": \"this access token does not exist\"}", 0);

        assertThatThrownBy(() -> verifierWithTimeouts(Duration.ofSeconds(2)).verify("expired-token"))
                .isInstanceOf(KakaoTokenInvalidException.class);
    }

    @Test
    void 응답_타임아웃이면_KakaoUnavailableException_장애경로() throws IOException {
        // read timeout 300ms인데 서버는 1s 지연 → ResourceAccessException(SocketTimeout) → 503 장애 경로.
        // M2-C(R-007 인접): 토큰 문제 아님 → 503 AUTH_KAKAO_UNAVAILABLE(재시도, 재로그인 아님).
        startServer(200, "{\"id\": 1}", 1000);

        assertThatThrownBy(() -> verifierWithTimeouts(Duration.ofMillis(300)).verify("slow-token"))
                .isInstanceOf(KakaoUnavailableException.class);
    }

    @Test
    void kapi_5xx이면_KakaoUnavailableException_장애경로() throws IOException {
        // 5xx는 토큰 자격 문제가 아닌 상위 장애 → 503 AUTH_KAKAO_UNAVAILABLE(계약 auth-api §1 v0.1.1).
        startServer(503, "{\"msg\": \"service unavailable\"}", 0);

        assertThatThrownBy(() -> verifierWithTimeouts(Duration.ofSeconds(2)).verify("any-token"))
                .isInstanceOf(KakaoUnavailableException.class);
    }

    @Test
    void 응답에_id가_없으면_KakaoTokenInvalidException() throws IOException {
        startServer(200, "{\"connected_at\": \"2026-01-01T00:00:00Z\"}", 0);

        assertThatThrownBy(() -> verifierWithTimeouts(Duration.ofSeconds(2)).verify("weird-token"))
                .isInstanceOf(KakaoTokenInvalidException.class);
    }
}
