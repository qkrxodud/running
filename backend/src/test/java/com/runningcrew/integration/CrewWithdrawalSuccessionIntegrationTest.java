package com.runningcrew.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.runningcrew.common.appversion.Platform;
import com.runningcrew.crew.application.CrewCommandService;
import com.runningcrew.crew.application.CrewQueryService;
import com.runningcrew.crew.application.view.CrewDetailView;
import com.runningcrew.crew.domain.Crew;
import com.runningcrew.crew.domain.CrewRole;
import com.runningcrew.crew.domain.CrewStatus;
import com.runningcrew.crew.application.port.out.CrewRepository;
import com.runningcrew.support.AbstractMySqlIntegrationTest;
import com.runningcrew.user.application.AuthService;
import com.runningcrew.user.application.LoginResult;
import com.runningcrew.user.application.UserAccountService;
import com.runningcrew.user.application.port.out.UserRepository;
import com.runningcrew.user.domain.User;
import com.runningcrew.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 탈퇴 → UserWithdrawn 동기 소비(멤버십 정리·크루장 승계·CLOSED) 통합 테스트(설계 §1.3·§3.3, planner AC).
 *
 * <p>실 MySQL(Testcontainers)에서 애플리케이션 서비스 → 도메인 → 영속 어댑터 전 경로를 검증한다.
 * 부팅 자체가 ddl-auto=validate로 B1 엔티티 매핑을 라이브 검증한다.
 */
class CrewWithdrawalSuccessionIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired AuthService authService;
    @Autowired UserAccountService userAccountService;
    @Autowired CrewCommandService crewCommandService;
    @Autowired CrewQueryService crewQueryService;
    @Autowired UserRepository userRepository;
    @Autowired CrewRepository crewRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 크루장_탈퇴시_최선임_승계__마지막_1인_탈퇴시_CLOSED() {
        long u1 = login("stub:succ-1");
        long u2 = login("stub:succ-2");
        long u3 = login("stub:succ-3");

        CrewDetailView created = crewCommandService.createCrew(u1, "승계 테스트 크루");
        long crewId = created.id();
        assertThat(created.leaderUserId()).isEqualTo(u1);
        assertThat(created.members()).hasSize(1);

        String code = crewCommandService.createInviteCode(u1, crewId, 10, 72).getCode();
        crewCommandService.joinCrew(u2, code);
        crewCommandService.joinCrew(u3, code);

        CrewDetailView full = crewQueryService.getCrewDetail(u1, crewId);
        assertThat(full.members()).hasSize(3);
        // members는 joined_at 오름차순 — u1(leader) 최선임
        assertThat(full.members().get(0).userId()).isEqualTo(u1);

        // 크루장(u1) 탈퇴 → 최선임(u2) 승계
        userAccountService.withdraw(u1);
        CrewDetailView afterU1 = crewQueryService.getCrewDetail(u2, crewId);
        assertThat(afterU1.leaderUserId()).isEqualTo(u2);
        assertThat(afterU1.members()).hasSize(2);
        assertThat(memberRole(afterU1, u2)).isEqualTo(CrewRole.LEADER);
        assertThat(afterU1.members()).noneMatch(m -> m.userId() == u1);   // 탈퇴자 명단 미노출

        // u1 익명화·kakao 파기 확인(설계 §1.3)
        User withdrawn = userRepository.findById(u1).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.getNickname()).isEqualTo("탈퇴한 러너");
        assertThat(withdrawn.getKakaoAccount()).isNull();

        // O-4: 동일 카카오 재로그인 = 신규 User(과거 기록 분리)
        long u1Again = login("stub:succ-1");
        assertThat(u1Again).isNotEqualTo(u1);

        // 다음 크루장(u2) 탈퇴 → u3 승계
        userAccountService.withdraw(u2);
        CrewDetailView afterU2 = crewQueryService.getCrewDetail(u3, crewId);
        assertThat(afterU2.leaderUserId()).isEqualTo(u3);
        assertThat(afterU2.members()).hasSize(1);

        // 마지막 1인(u3) 탈퇴 → 크루 CLOSED
        userAccountService.withdraw(u3);
        Crew closed = crewRepository.findById(crewId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(CrewStatus.CLOSED);
    }

    @Test
    void 탈퇴시_디바이스_토큰이_파기된다() {
        long u = login("stub:device-1");
        userAccountService.registerDeviceToken(u, "fcm-token-xyz", Platform.ANDROID);
        assertThat(deviceTokenCount(u)).isEqualTo(1);

        userAccountService.withdraw(u);
        assertThat(deviceTokenCount(u)).isEqualTo(0);   // 식별 정보 즉시 파기(설계 §1.3 step4)
    }

    private long login(String stubToken) {
        LoginResult r = authService.login(stubToken);
        return r.user().getId();
    }

    private int deviceTokenCount(long userId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM device_token WHERE user_id = ?", Integer.class, userId);
        return c == null ? 0 : c;
    }

    private static CrewRole memberRole(CrewDetailView view, long userId) {
        return view.members().stream()
                .filter(m -> m.userId() == userId)
                .findFirst().orElseThrow()
                .role();
    }
}
