package com.runningcrew.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 실 MySQL 8(Testcontainers) 위의 @SpringBootTest 공통 베이스. 컨테이너를 <b>싱글턴</b>으로 재사용해
 * 여러 통합 테스트가 하나의 MySQL·하나의 스프링 컨텍스트를 공유한다.
 *
 * <p>{@code local} 프로필로 부팅 → 스텁 카카오 검증기 활성 + JWT_SECRET 기본값. Flyway(V1·V2) 적용 후
 * {@code ddl-auto: validate}가 B1 엔티티 5종 매핑을 라이브 검증한다(B1-S6 AC — 부팅 성공 = validate 통과).
 */
@SpringBootTest
@ActiveProfiles("local")
public abstract class AbstractMySqlIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("runningcrew")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true")
            .withCommand("--default-time-zone=+00:00",
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci");

    static {
        MYSQL.start();   // 싱글턴 — JVM 종료 시까지 재사용(테스트별 재기동 없음)
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
