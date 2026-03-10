package com.kronos.entity;

import com.kronos.entity.enums.RevisionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "revision_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevisionSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_plan_id", nullable = false)
    private RevisionPlan revisionPlan;

    @Column(nullable = false)
    private int dayNumber; // which day in the plan (e.g., 4)

    @Column(nullable = false)
    private LocalDate scheduledDate; // createdAt.toLocalDate() + dayNumber

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RevisionStatus status;

    private LocalDateTime completedAt;

    private String googleCalendarEventId;

    private boolean notificationSent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}