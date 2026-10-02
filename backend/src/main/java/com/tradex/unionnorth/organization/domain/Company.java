package com.tradex.unionnorth.organization.domain;

import com.tradex.unionnorth.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "companies", uniqueConstraints = @UniqueConstraint(name = "uk_companies_code", columnNames = "code"))
public class Company extends AuditableEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "registration_number", length = 80)
    private String registrationNumber;

    @Column(nullable = false)
    private boolean active = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
