package com.rinda.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cooperatives")
public class Cooperative {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String district;

    private String sector;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    protected Cooperative() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDistrict() {
        return district;
    }

    public String getSector() {
        return sector;
    }

    public boolean isDemo() {
        return demo;
    }
}
