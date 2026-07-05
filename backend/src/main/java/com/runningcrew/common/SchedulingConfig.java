package com.runningcrew.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러·비동기 활성화. {@code @Scheduled}(마감 A9·리마인더 M3-C)와 {@code @Async}(리플레이 스냅샷 생성
 * AFTER_COMMIT, M3-A A5)가 동작하도록 한다.
 *
 * <p>스케줄·마감 판정 시각은 주입된 {@link java.time.Clock}(UTC — {@link TimeConfig})을 쓰므로 테스트에서
 * clock을 고정해 전이를 재현할 수 있다. 리플레이 생성은 AFTER_COMMIT + @Async로 확정 트랜잭션과 분리(RP-9).
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
