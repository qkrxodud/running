package com.runningcrew.race.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.race.application.port.out.CourseQueryPort;
import com.runningcrew.race.application.port.out.CourseRepository;
import com.runningcrew.race.application.port.out.CrewAccessPort;
import com.runningcrew.race.application.port.out.CrewAccessPort.CrewRef;
import com.runningcrew.race.application.port.out.PromotionSourcePort;
import com.runningcrew.race.application.port.out.PromotionSourcePort.PromotionSource;
import com.runningcrew.race.application.view.CourseDetailView;
import com.runningcrew.race.domain.Course;
import com.runningcrew.race.domain.InvalidCourseException;
import com.runningcrew.race.domain.LatLng;
import com.runningcrew.race.domain.PolylineCodec;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 명령 유스케이스(course-api.md §1·§4). §1 크루장 전용 생성 — distance_m는 서버가 폴리라인에서 확정.
 * §4 승격 — 크루 ACTIVE 멤버·본인 FINISHED 트랙만, distance·좌표는 refined에서 서버 재확정(C6).
 */
@Service
public class CourseCommandService {

    private final CourseRepository courseRepository;
    private final CourseQueryPort courseQueryPort;
    private final CrewAccessPort crewAccessPort;
    private final PromotionSourcePort promotionSourcePort;
    private final int promotionMinDistanceM;
    private final Clock clock;

    public CourseCommandService(CourseRepository courseRepository,
                                CourseQueryPort courseQueryPort,
                                CrewAccessPort crewAccessPort,
                                PromotionSourcePort promotionSourcePort,
                                @Value("${promotion.min-distance-m:1000}") int promotionMinDistanceM,
                                Clock clock) {
        this.courseRepository = courseRepository;
        this.courseQueryPort = courseQueryPort;
        this.crewAccessPort = crewAccessPort;
        this.promotionSourcePort = promotionSourcePort;
        this.promotionMinDistanceM = promotionMinDistanceM;
        this.clock = clock;
    }

    /** 크루장 전용 코스 생성. 201 CourseDetail. */
    @Transactional
    public CourseDetailView createCourse(Long userId, Long crewId, CreateCourseCommand command) {
        CrewRef crew = crewAccessPort.findCrew(crewId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!crew.isLeader(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 크루장 아님(CO-B4)
        }
        if (crew.closed()) {
            throw new ApiException(ErrorCode.CREW_CLOSED);
        }
        Course course;
        try {
            course = Course.create(crewId, command.name(), command.routePolyline(),
                    new LatLng(command.startLat(), command.startLng()),
                    new LatLng(command.finishLat(), command.finishLng()),
                    userId, clock.instant());
        } catch (InvalidCourseException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
        Course saved = courseRepository.save(course);
        return courseQueryPort.findDetail(saved.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /**
     * 과거 주행 코스 승격(course-api §4 / C6). 크루 ACTIVE 멤버·본인 FINISHED 트랙·거리 하한 충족 시
     * refined 경로로 새 불변 Course 발행. 평가 순서: 404(크루/트랙) → 403(비멤버/타인) → 409(CLOSED/자격).
     * distance·start/finish는 refined 폴리라인에서 서버 재확정(CO-B3/PR-4).
     */
    @Transactional
    public CourseDetailView promoteCourse(Long userId, Long crewId, PromoteCourseCommand command) {
        CrewRef crew = crewAccessPort.findCrew(crewId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));            // 크루 없음 → 404
        if (!crewAccessPort.isActiveMember(crewId, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 크루 ACTIVE 멤버 아님(PR-1)
        }
        if (crew.closed()) {
            throw new ApiException(ErrorCode.CREW_CLOSED);
        }
        PromotionSource src = promotionSourcePort.find(command.sourceTrackRecordId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));            // 트랙 없음 → 404
        if (!src.ownerUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 타인 트랙(존재 누설 방지 — PR-1)
        }
        if (!src.finished()) {
            throw new ApiException(ErrorCode.COURSE_PROMOTION_INELIGIBLE,
                    "완주(FINISHED)한 트랙만 코스로 승격할 수 있습니다.");   // DNF(PR-2)
        }
        if (src.totalDistanceM() < promotionMinDistanceM) {
            throw new ApiException(ErrorCode.COURSE_PROMOTION_INELIGIBLE,
                    "코스 승격 최소 거리(" + promotionMinDistanceM + "m) 미만입니다.");   // PR-3
        }
        String polyline = src.refinedPolyline();
        if (polyline == null || polyline.isBlank()) {
            throw new ApiException(ErrorCode.COURSE_PROMOTION_INELIGIBLE,
                    "정제 경로가 없어 코스로 승격할 수 없습니다.");
        }
        List<LatLng> points = PolylineCodec.decode(polyline);
        if (points.size() < Course.MIN_POINTS) {
            throw new ApiException(ErrorCode.COURSE_PROMOTION_INELIGIBLE, "정제 경로 포인트가 부족합니다.");
        }
        LatLng start = points.get(0);
        LatLng finish = points.get(points.size() - 1);
        Course course;
        try {
            // refined 폴리라인으로 Course 생성 — distance_m·좌표는 여기서 서버 재확정(PR-4).
            course = Course.create(crewId, command.name(), polyline, start, finish,
                    userId, clock.instant());
        } catch (InvalidCourseException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
        Course saved = courseRepository.save(course);
        return courseQueryPort.findDetail(saved.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
