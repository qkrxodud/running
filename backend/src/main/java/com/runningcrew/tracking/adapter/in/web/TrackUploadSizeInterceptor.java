package com.runningcrew.tracking.adapter.in.web;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * track-api TK-3 본문 바이트 상한(≤8 MiB, 외부화) 가드 (W46-1 / R-006).
 *
 * <p>{@code Content-Length} 프리체크로 <b>본문 버퍼링·폴리라인 디코딩 이전</b>에 차단한다 —
 * 거대 폴리라인 문자열이 디코딩 전 전량 버퍼링되는 위험(R-006)을 막는다. preHandle에서 던지는
 * {@link ApiException}은 DispatcherServlet의 예외 리졸버를 거쳐 {@code GlobalExceptionHandler}가
 * {@code {code,message}} 규약으로 413 TRACK_TOO_LARGE를 반환한다(서블릿 컨테이너 하드 차단과 달리
 * 계약 오류 shape 유지).
 *
 * <p>포인트 수 상한(20,000)은 디코딩 후 {@code TrackUploadService}가 별도로 강제한다 — 바이트·포인트 쌍 가드.
 */
public class TrackUploadSizeInterceptor implements HandlerInterceptor {

    private final long maxRequestBytes;

    public TrackUploadSizeInterceptor(long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            long contentLength = request.getContentLengthLong();
            // Content-Length 부재(-1, 청크 전송)는 여기서 통과 — 포인트 수 상한이 2차 방어.
            if (contentLength > maxRequestBytes) {
                throw new ApiException(ErrorCode.TRACK_TOO_LARGE);
            }
        }
        return true;
    }
}
