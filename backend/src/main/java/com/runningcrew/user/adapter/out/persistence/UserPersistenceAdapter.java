package com.runningcrew.user.adapter.out.persistence;

import com.runningcrew.user.application.port.out.UserRepository;
import com.runningcrew.user.domain.KakaoAccount;
import com.runningcrew.user.domain.User;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link UserRepository} 구현 — 도메인 {@link User} ↔ {@link UserJpaEntity} 매핑.
 */
@Repository
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public UserPersistenceAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpa.findById(id).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findByKakaoId(String kakaoId) {
        return jpa.findByKakaoId(kakaoId).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public User save(User user) {
        UserJpaEntity saved = jpa.save(toEntity(user));
        return toDomain(saved);
    }

    private static User toDomain(UserJpaEntity e) {
        KakaoAccount account = e.getKakaoId() == null ? null : new KakaoAccount(e.getKakaoId());
        return new User(e.getId(), e.getNickname(), account, e.getStatus(),
                e.getCreatedAt(), e.getWithdrawnAt(), e.getOnboardedAt());
    }

    private static UserJpaEntity toEntity(User u) {
        String kakaoId = u.getKakaoAccount() == null ? null : u.getKakaoAccount().kakaoId();
        return new UserJpaEntity(u.getId(), u.getNickname(), kakaoId, u.getStatus(),
                u.getCreatedAt(), u.getWithdrawnAt(), u.getOnboardedAt());
    }
}
