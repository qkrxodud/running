package com.runningcrew.crew.adapter.out.persistence;

import com.runningcrew.crew.application.port.out.InviteCodeRepository;
import com.runningcrew.crew.domain.InviteCode;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link InviteCodeRepository} 구현 — 도메인 {@link InviteCode} ↔ JPA 엔티티 매핑.
 */
@Repository
public class InviteCodePersistenceAdapter implements InviteCodeRepository {

    private final InviteCodeJpaRepository jpa;

    public InviteCodePersistenceAdapter(InviteCodeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<InviteCode> findByCodeForUpdate(String code) {
        return jpa.findByCodeForUpdate(code).map(InviteCodePersistenceAdapter::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpa.existsById(code);
    }

    @Override
    public InviteCode save(InviteCode ic) {
        InviteCodeJpaEntity entity = jpa.findById(ic.getCode())
                .map(existing -> {
                    existing.updateUsedCount(ic.getUsedCount());
                    return existing;
                })
                .orElseGet(() -> new InviteCodeJpaEntity(ic.getCode(), ic.getCrewId(),
                        ic.getExpiresAt(), ic.getMaxUses(), ic.getUsedCount()));
        return toDomain(jpa.saveAndFlush(entity));
    }

    private static InviteCode toDomain(InviteCodeJpaEntity e) {
        return new InviteCode(e.getCode(), e.getCrewId(), e.getExpiresAt(), e.getMaxUses(),
                e.getUsedCount());
    }
}
