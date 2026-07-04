package com.runningcrew.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각/직렬화 규약(계약 conventions.md §3).
 *
 * <ul>
 *   <li>저장·비교·판정은 전부 UTC. JVM 기본 타임존은 {@code RunningCrewApplication}에서 UTC 고정,
 *       MySQL 세션 타임존은 {@code application.yml}의 {@code hibernate.jdbc.time_zone=UTC}와
 *       JDBC URL {@code connectionTimeZone=UTC}로 고정.
 *   <li>Jackson은 {@code Instant}를 ISO-8601 + UTC(Z 오프셋 명시) 문자열로 직렬화한다. 타임스탬프
 *       숫자(epoch) 직렬화를 끄고 {@link JavaTimeModule}을 등록한다.
 * </ul>
 */
@Configuration
public class TimeConfig {

    /**
     * 애플리케이션 전역 Jackson 설정. {@code application.yml}의 jackson 속성과 함께 적용되며,
     * 코드로도 명시해 회귀(숫자 타임스탬프 직렬화)를 막는다.
     */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer timeSerializationCustomizer() {
        return builder -> {
            builder.modules(new JavaTimeModule());
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.timeZone("UTC");
        };
    }

    /**
     * 슬라이스 테스트(@WebMvcTest 등)에서도 동일 규약이 적용되도록 ObjectMapper 후처리 훅을 남긴다.
     * 실사용 컨텍스트에서는 위 customizer가 부트의 자동 구성에 반영된다.
     */
    static void applyDefaults(ObjectMapper mapper) {
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    }
}
