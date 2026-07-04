package com.runningcrew.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 인증된 내부 user id(Long)를 주입한다.
 *
 * <p>인증 필터가 요청 속성에 세팅한 값을 {@link AuthenticatedUserArgumentResolver}가 해석한다.
 * 컨트롤러는 카카오 회원번호가 아닌 내부 user id만 다룬다(봉인 원칙, backend-hexagonal 스킬).
 * common에 두어 user·crew 등 어느 컨텍스트 컨트롤러도 컨텍스트 간 클래스 의존 없이 사용한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUserId {
}
