package com.runningcrew.user.application.port.out;

import com.runningcrew.user.domain.User;
import java.util.Optional;

/**
 * User 애그리거트 영속 out-port. 어댑터가 도메인 ↔ JPA 엔티티 매핑을 담당한다.
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    /** 탈퇴 처리용 비관적 잠금 조회(설계 §1.3 — FOR UPDATE). */
    Optional<User> findByIdForUpdate(Long id);

    /** 카카오 회원번호로 조회(로그인 — 있으면 재사용, 없으면 신규 생성). */
    Optional<User> findByKakaoId(String kakaoId);

    /** 신규 저장(반환은 id가 채워진 도메인 객체) 및 기존 갱신. */
    User save(User user);
}
