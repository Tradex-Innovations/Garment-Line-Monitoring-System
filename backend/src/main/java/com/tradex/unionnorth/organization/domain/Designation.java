package com.tradex.unionnorth.organization.domain;

import com.tradex.unionnorth.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "designations", uniqueConstraints = @UniqueConstraint(name = "uk_designations_company_code", columnNames = {"company_id", "code"}))
public class Designation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
