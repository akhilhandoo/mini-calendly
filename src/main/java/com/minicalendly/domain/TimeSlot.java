package com.minicalendly.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "time_slot")
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "time_slot_seq")
    @SequenceGenerator(name = "time_slot_seq", sequenceName = "time_slot_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimeSlot() {
    }

    public TimeSlot(Calendar calendar, Instant startTime, Instant endTime, SlotStatus status, Instant now) {
        this.calendar = calendar;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void reschedule(Instant startTime, Instant endTime, Instant now) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.updatedAt = now;
    }

    public void changeStatus(SlotStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public Duration duration() {
        return Duration.between(startTime, endTime);
    }

    public Long getId() {
        return id;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
