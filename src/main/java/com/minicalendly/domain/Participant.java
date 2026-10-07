package com.minicalendly.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A meeting attendee. Participants are identified by e-mail; when the e-mail
 * belongs to a registered user the link is kept so the meeting shows up in
 * that user's meeting list as well.
 */
@Embeddable
public class Participant {

    @Column(nullable = false)
    private String email;

    private String name;

    @Column(name = "user_id")
    private Long userId;

    protected Participant() {
    }

    public Participant(String email, String name, Long userId) {
        this.email = email;
        this.name = name;
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public Long getUserId() {
        return userId;
    }
}
