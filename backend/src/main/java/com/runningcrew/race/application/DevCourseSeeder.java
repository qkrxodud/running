package com.runningcrew.race.application;

import com.runningcrew.race.application.port.out.CourseSeedPort;
import com.runningcrew.race.application.port.out.CourseSeedPort.SeedTargetCrew;
import com.runningcrew.race.application.port.out.CourseRepository;
import com.runningcrew.race.domain.Course;
import com.runningcrew.race.domain.LatLng;
import com.runningcrew.race.domain.PolylineCodec;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * dev 시드 코스(설계 §1.4). <b>{@code @Profile({"local","dev"})} 전용</b> — prod 미유출(CO-B7).
 * Flyway 마이그레이션은 prod에도 실행되므로 지양하고, 프로필 분기가 되는 시더 빈으로 넣는다.
 *
 * <p>부팅 시 ACTIVE 크루가 있으면 그 크루장 명의로 더미 코스 2개를 upsert(이름 기준 멱등 — 재기동 중복 없음).
 * 크루가 없으면 no-op(통합 테스트 부팅에도 무해). 좌표는 지도 SDK 확보 전 하드코딩(서울 한강 근사)이며
 * 폴리라인은 서버 인코딩 경로(1e5), distance_m은 서버 계산 경로를 그대로 통과한다.
 */
@Component
@Profile({"local", "dev"})
public class DevCourseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevCourseSeeder.class);

    private final CourseSeedPort seedPort;
    private final CourseRepository courseRepository;
    private final Clock clock;

    public DevCourseSeeder(CourseSeedPort seedPort, CourseRepository courseRepository, Clock clock) {
        this.seedPort = seedPort;
        this.courseRepository = courseRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<SeedTargetCrew> target = seedPort.findSeedTargetCrew();
        if (target.isEmpty()) {
            log.info("[dev-seed] ACTIVE 크루 없음 — 시드 코스 생략(no-op)");
            return;
        }
        SeedTargetCrew crew = target.get();
        for (SeedDef def : SEED_DEFS) {
            if (seedPort.courseExists(crew.crewId(), def.name())) {
                continue;   // 멱등 — 재기동 중복 방지
            }
            Course course = Course.create(crew.crewId(), def.name(),
                    PolylineCodec.encode(def.points()),
                    def.points().getFirst(), def.points().getLast(),
                    crew.leaderId(), clock.instant());
            Course saved = courseRepository.save(course);
            log.info("[dev-seed] 코스 생성: crew={} name='{}' distance_m={} id={}",
                    crew.crewId(), saved.getName(), saved.getDistanceM(), saved.getId());
        }
    }

    private record SeedDef(String name, List<LatLng> points) {
    }

    // 서울 한강/남산 근사 좌표(지도 SDK 확보 전 임시). 세션 생성 UI(B2-C2) 언블록용.
    private static final List<SeedDef> SEED_DEFS = List.of(
            new SeedDef("한강 반포-잠실 5K", List.of(
                    new LatLng(37.51235, 126.99640),
                    new LatLng(37.51188, 127.00412),
                    new LatLng(37.51602, 127.01105),
                    new LatLng(37.52014, 127.02330),
                    new LatLng(37.51930, 127.03588),
                    new LatLng(37.51547, 127.04701))),
            new SeedDef("남산 순환 3K", List.of(
                    new LatLng(37.55130, 126.98800),
                    new LatLng(37.55045, 126.99320),
                    new LatLng(37.55360, 126.99610),
                    new LatLng(37.55620, 126.99210),
                    new LatLng(37.55380, 126.98740))));
}
