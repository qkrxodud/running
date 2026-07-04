package com.runningcrew.race.domain;

import java.time.Instant;

/**
 * Course 애그리거트 루트(설계 22 §1). <b>불변 애그리거트</b> — 생성 후 변경 없음(수정/삭제 API 미노출).
 * 순수 도메인(ArchUnit R-1) — Spring/JPA 무관.
 *
 * <p>불변식(CO-B*): 폴리라인 precision 1e5·상호운용(CO-B1), distance_m는 서버가 폴리라인에서 확정(CO-B3),
 * 발행 후 불변(구조 — 수정/삭제 메서드 부재, CO-B5).
 */
public class Course {

    public static final int NAME_MIN = 1;
    public static final int NAME_MAX = 50;
    public static final int MIN_POINTS = 2;

    private final Long id;
    private final Long crewId;
    private final String name;
    private final RoutePath routePath;
    private final int distanceM;
    private final LatLng startPoint;
    private final LatLng finishPoint;
    private final Long createdBy;
    private final Instant createdAt;

    public Course(Long id, Long crewId, String name, RoutePath routePath, int distanceM,
                  LatLng startPoint, LatLng finishPoint, Long createdBy, Instant createdAt) {
        this.id = id;
        this.crewId = crewId;
        this.name = name;
        this.routePath = routePath;
        this.distanceM = distanceM;
        this.startPoint = startPoint;
        this.finishPoint = finishPoint;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /**
     * 코스 생성. 폴리라인을 디코딩해(≥2점) 총거리를 서버가 확정하고 좌표 범위를 검증한다.
     * {@code distance_m}는 클라 제출값을 받지 않고 여기서 계산한다(CO-B3).
     *
     * @throws InvalidCourseException 이름·폴리라인·좌표 범위 위반
     */
    public static Course create(Long crewId, String rawName, String encodedPolyline,
                                LatLng startPoint, LatLng finishPoint, Long createdBy, Instant now) {
        String name = normalizeName(rawName);
        RoutePath routePath = RoutePath.fromEncoded(encodedPolyline);
        if (routePath.points().size() < MIN_POINTS) {
            throw new InvalidCourseException("코스 경로는 최소 " + MIN_POINTS + "점이어야 합니다.");
        }
        for (LatLng p : routePath.points()) {
            requireInRange(p);
        }
        requireInRange(startPoint);
        requireInRange(finishPoint);
        int distanceM = GeoDistance.totalMeters(routePath.points());
        return new Course(null, crewId, name, routePath, distanceM, startPoint, finishPoint,
                createdBy, now);
    }

    private static String normalizeName(String rawName) {
        if (rawName == null) {
            throw new InvalidCourseException("코스명은 필수입니다.");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() < NAME_MIN || trimmed.length() > NAME_MAX) {
            throw new InvalidCourseException("코스명은 1~50자여야 합니다.");
        }
        return trimmed;
    }

    private static void requireInRange(LatLng p) {
        if (p == null) {
            throw new InvalidCourseException("좌표는 필수입니다.");
        }
        if (p.lat() < -90.0 || p.lat() > 90.0) {
            throw new InvalidCourseException("위도 범위 오류: " + p.lat());
        }
        if (p.lng() < -180.0 || p.lng() > 180.0) {
            throw new InvalidCourseException("경도 범위 오류: " + p.lng());
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCrewId() {
        return crewId;
    }

    public String getName() {
        return name;
    }

    public String getRoutePolyline() {
        return routePath.encoded();
    }

    public RoutePath getRoutePath() {
        return routePath;
    }

    public int getDistanceM() {
        return distanceM;
    }

    public LatLng getStartPoint() {
        return startPoint;
    }

    public LatLng getFinishPoint() {
        return finishPoint;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
