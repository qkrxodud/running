package com.runningcrew.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R-003 재현·재발 방지 테스트 (골든 테스트 스킬 "버그 → 재현 테스트 변환" 절차).
 *
 * <p><b>버그</b>: {@code rank}는 MySQL 8.0.2+ 예약어인데 {@code V1__init.sql}의
 * rank_entry·reward_item 컬럼명에 백틱 없이 사용돼 Flyway가 ERROR 1064로 실패,
 * 앱 부팅 불가(QA 2차 B2-1, 라이브 재현됨). 웹 슬라이스 테스트는 DB 미접촉이라
 * 이 유형을 구조적으로 검출할 수 없다 → 실 MySQL 8에 마이그레이션 전체를 적용하는
 * 이 테스트가 유일한 자동 방어선이다.
 *
 * <p>red→green 증명: 이 테스트는 백틱 수정 <b>전</b> SQL로 실패(red)를 먼저 확인한 뒤
 * 수정 후 통과(green)를 확인하는 순서로 박제되었다.
 */
@Testcontainers
class R003FlywayMigrationLiveTest {

    /** compose(mysql:8.0)와 동일 메이저·마이너의 실 MySQL. */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("runningcrew")
            .withCommand("--default-time-zone=+00:00",
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci");

    /** 설계문서 §2 기준 17개 물리 테이블 (§2.14 = reward_plan + reward_item). */
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "user", "device_token", "crew", "crew_member", "invite_code",
            "course", "race_session", "participation", "track_record", "track_payload",
            "race_result", "rank_entry", "replay_snapshot",
            "reward_plan", "reward_item", "reward_grant", "app_min_version");

    @Test
    void R003_rank_예약어_회귀방지__V1_마이그레이션이_실MySQL8에_전체_적용되고_17개_테이블이_생성된다() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        // R-003 증상: 여기서 ERROR 1064 (near 'rank INT ...') 로 FlywayException 발생했다.
        var result = flyway.migrate();
        assertThat(result.success).isTrue();

        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {

            // 17개 테이블 전수 생성 확인 (R-003 당시엔 12개만 부분 생성됐다)
            Set<String> actualTables = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = ? AND table_type = 'BASE TABLE'")) {
                ps.setString(1, "runningcrew");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        actualTables.add(rs.getString(1));
                    }
                }
            }
            actualTables.remove("flyway_schema_history");
            assertThat(actualTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);

            // 컬럼명은 계약·설계대로 `rank` 유지 확인 (이름 변경 아님 — 백틱 인용만)
            assertThat(columnExists(conn, "rank_entry", "rank")).isTrue();
            assertThat(columnExists(conn, "reward_item", "rank")).isTrue();
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'runningcrew' AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }
}
