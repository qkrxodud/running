package com.runningcrew.crew.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InviteCodeJpaRepository extends JpaRepository<InviteCodeJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InviteCodeJpaEntity i where i.code = :code")
    Optional<InviteCodeJpaEntity> findByCodeForUpdate(@Param("code") String code);
}
