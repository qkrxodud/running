package com.runningcrew.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 활성화(M2-A A9 마감 스케줄러). {@code @Scheduled} 빈이 동작하도록 한다.
 *
 * <p>마감 판정 시각은 주입된 {@link java.time.Clock}(UTC — {@link TimeConfig})을 쓰므로 테스트에서
 * clock을 고정해 deadline 전이를 재현할 수 있다(A9 수용 기준).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
