package com.minicalendly.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.ZoneId;

/**
 * A user's personal calendar: the aggregate that owns their time slots.
 * It is purely a domain concept; the API addresses it through its owner.
 */
@Entity
@Table(name = "calendar")
public class Calendar {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "calendar_seq")
    @SequenceGenerator(name = "calendar_seq", sequenceName = "calendar_seq", allocationSize = 50)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Calendar() {
    }

    public Calendar(User owner, ZoneId timeZone, Instant createdAt) {
        this.owner = owner;
        this.timeZone = timeZone.getId();
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public ZoneId getTimeZone() {
        return ZoneId.of(timeZone);
    }
}
