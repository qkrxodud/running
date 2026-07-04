package com.runningcrew.user.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DeviceTokenJpaRepository extends JpaRepository<DeviceTokenJpaEntity, Long> {

    Optional<DeviceTokenJpaEntity> findByFcmToken(String fcmToken);

    @Modifying
    @Query("delete from DeviceTokenJpaEntity d where d.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
