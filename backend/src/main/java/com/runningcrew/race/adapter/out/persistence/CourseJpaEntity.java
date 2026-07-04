package com.runningcrew.race.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** {@code course} 테이블 매핑(설계 §2.6). 발행 후 불변 — 갱신 메서드 없음. */
@Entity
@Table(name = "course")
public class CourseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "crew_id", nullable = false)
    private Long crewId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    // route_polyline은 LONGTEXT(Types#LONGVARCHAR). @Lob은 CLOB(tinytext)로 잡혀 validate가 갈리므로
    // JDBC 타입을 LONGVARCHAR로 고정한다(스키마 진실=Flyway V1, Hibernate는 검증만).
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "route_polyline", nullable = false)
    private String routePolyline;

    @Column(name = "distance_m", nullable = false)
    private int distanceM;

    @Column(name = "start_lat", nullable = false)
    private double startLat;

    @Column(name = "start_lng", nullable = false)
    private double startLng;

    @Column(name = "finish_lat", nullable = false)
    private double finishLat;

    @Column(name = "finish_lng", nullable = false)
    private double finishLng;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CourseJpaEntity() {
    }

    public CourseJpaEntity(Long id, Long crewId, String name, String routePolyline, int distanceM,
                           double startLat, double startLng, double finishLat, double finishLng,
                           Long createdBy, Instant createdAt) {
        this.id = id;
        this.crewId = crewId;
        this.name = name;
        this.routePolyline = routePolyline;
        this.distanceM = distanceM;
        this.startLat = startLat;
        this.startLng = startLng;
        this.finishLat = finishLat;
        this.finishLng = finishLng;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
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
        return routePolyline;
    }

    public int getDistanceM() {
        return distanceM;
    }

    public double getStartLat() {
        return startLat;
    }

    public double getStartLng() {
        return startLng;
    }

    public double getFinishLat() {
        return finishLat;
    }

    public double getFinishLng() {
        return finishLng;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
