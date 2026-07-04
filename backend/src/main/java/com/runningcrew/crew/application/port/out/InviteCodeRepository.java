package com.runningcrew.crew.application.port.out;

import com.runningcrew.crew.domain.InviteCode;
import java.util.Optional;

/**
 * 초대 코드 영속 out-port. 참가 경합 직렬화를 위해 코드 행 비관적 잠금 조회를 제공한다(설계 §3.2).
 */
public interface InviteCodeRepository {

    /** 참가 처리용 비관적 쓰기 잠금 조회(동시 참가 직렬화). */
    Optional<InviteCode> findByCodeForUpdate(String code);

    boolean existsByCode(String code);

    InviteCode save(InviteCode inviteCode);
}
