package com.runningcrew.crew.adapter.out.persistence;

import com.runningcrew.crew.domain.CrewStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code crew} 테이블 매핑(설계 §2.3). 멤버는 {@link CrewMemberJpaEntity} 컬렉션(cascade ALL).
 */
@Entity
@Table(name = "crew")
public class CrewJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "leader_id", nullable = false)
    private Long leaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CrewStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "crew", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CrewMemberJpaEntity> members = new ArrayList<>();

    protected CrewJpaEntity() {
    }

    public CrewJpaEntity(Long id, String name, Long leaderId, CrewStatus status, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.leaderId = leaderId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void addMember(CrewMemberJpaEntity member) {
        member.setCrew(this);
        this.members.add(member);
    }

    public void updateScalars(String name, Long leaderId, CrewStatus status) {
        this.name = name;
        this.leaderId = leaderId;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public CrewStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<CrewMemberJpaEntity> getMembers() {
        return members;
    }
}
