package com.tradex.unionnorth.organization.repository;

import com.tradex.unionnorth.organization.domain.Company;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByCode(String code);
}
