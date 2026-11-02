package com.rinda.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "farmers")
public class Farmer {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "cooperative_id", nullable = false)
    private long cooperativeId;

    protected Farmer() {
    }

    public Farmer(long userId, long cooperativeId) {
        this.userId = userId;
        this.cooperativeId = cooperativeId;
    }

    public long getCooperativeId() {
        return cooperativeId;
    }
}
