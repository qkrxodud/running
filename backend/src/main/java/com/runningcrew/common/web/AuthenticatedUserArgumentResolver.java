package com.runningcrew.common.web;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link AuthUserId}가 붙은 {@code Long} 파라미터에 인증 필터가 세팅한 내부 user id를 주입한다.
 *
 * <p>속성이 없으면(=필터를 통과하지 못한 보호 경로) 401 {@code UNAUTHORIZED}. 정상 흐름에선
 * 필터가 이미 검증했으므로 방어적 이중 확인이다.
 */
public class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Object userId = webRequest.getAttribute(
                AuthAttributes.AUTH_USER_ID, RequestAttributes.SCOPE_REQUEST);
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
