package com.runningcrew.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.support.AbstractMySqlIntegrationTest;
import com.runningcrew.user.application.port.out.UserRepository;
import com.runningcrew.user.domain.KakaoAccount;
import com.runningcrew.user.domain.User;
import com.runningcrew.user.domain.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Instant 왕복(저장→조회 무손실) 라이브 검증(B1-S6 AC ③, QA 이월 4 해소). TIMESTAMP(6) 마이크로초까지
 * 오프셋 손실 없이 UTC로 복원되는지 실 MySQL에서 확인한다.
 */
class InstantRoundtripLiveTest extends AbstractMySqlIntegrationTest {

    @Autowired UserRepository userRepository;

    @Test
    void createdAt_마이크로초까지_무손실_왕복된다() {
        Instant precise = Instant.parse("2026-07-04T09:30:00.123456Z");
        User saved = userRepository.save(
                new User(null, "왕복테스트", new KakaoAccount("roundtrip-1"),
                        UserStatus.ACTIVE, precise, null, null));

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(precise);
    }
}
