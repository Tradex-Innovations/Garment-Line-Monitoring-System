package com.tradex.unionnorth.organization.repository;

import com.tradex.unionnorth.organization.domain.Designation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {
}
