package com.runningcrew.crew.adapter.out.persistence;

import com.runningcrew.crew.application.port.out.CrewRepository;
import com.runningcrew.crew.domain.Crew;
import com.runningcrew.crew.domain.CrewMember;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link CrewRepository} 구현 — 도메인 {@link Crew}(멤버 포함) ↔ JPA 엔티티 그래프 매핑/reconcile.
 *
 * <p>멤버는 삭제하지 않는다(탈퇴는 status 플립). 신규 멤버(id=null)는 insert, 기존은 update로 reconcile.
 */
@Repository
public class CrewPersistenceAdapter implements CrewRepository {

    private final CrewJpaRepository jpa;

    public CrewPersistenceAdapter(CrewJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Crew> findById(Long id) {
        return jpa.findWithMembersById(id).map(CrewPersistenceAdapter::toDomain);
    }

    @Override
    public List<Crew> findAllByActiveMemberUserId(Long userId) {
        return jpa.findAllWithMembersByActiveMemberUserId(userId).stream()
                .map(CrewPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Crew save(Crew crew) {
        if (crew.getId() == null) {
            CrewJpaEntity entity = new CrewJpaEntity(null, crew.getName(), crew.getLeaderId(),
                    crew.getStatus(), crew.getCreatedAt());
            for (CrewMember m : crew.getMembers()) {
                entity.addMember(new CrewMemberJpaEntity(null, m.getUserId(), m.getRole(),
                        m.getJoinedAt(), m.getStatus()));
            }
            return toDomain(jpa.saveAndFlush(entity));
        }

        CrewJpaEntity entity = jpa.findWithMembersById(crew.getId())
                .orElseThrow(() -> new IllegalStateException("크루가 존재하지 않습니다: " + crew.getId()));
        entity.updateScalars(crew.getName(), crew.getLeaderId(), crew.getStatus());

        Map<Long, CrewMemberJpaEntity> existingById = new HashMap<>();
        for (CrewMemberJpaEntity me : entity.getMembers()) {
            existingById.put(me.getId(), me);
        }
        for (CrewMember dm : crew.getMembers()) {
            if (dm.getId() != null && existingById.containsKey(dm.getId())) {
                existingById.get(dm.getId()).update(dm.getRole(), dm.getJoinedAt(), dm.getStatus());
            } else {
                entity.addMember(new CrewMemberJpaEntity(null, dm.getUserId(), dm.getRole(),
                        dm.getJoinedAt(), dm.getStatus()));
            }
        }
        return toDomain(jpa.saveAndFlush(entity));
    }

    private static Crew toDomain(CrewJpaEntity e) {
        List<CrewMember> members = e.getMembers().stream()
                .map(m -> new CrewMember(m.getId(), m.getUserId(), m.getRole(), m.getJoinedAt(),
                        m.getStatus()))
                .toList();
        return new Crew(e.getId(), e.getName(), e.getLeaderId(), e.getStatus(), e.getCreatedAt(),
                members);
    }
}
