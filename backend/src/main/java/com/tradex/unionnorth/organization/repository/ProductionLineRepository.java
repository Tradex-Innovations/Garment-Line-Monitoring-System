package com.tradex.unionnorth.organization.repository;

import com.tradex.unionnorth.organization.domain.ProductionLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionLineRepository extends JpaRepository<ProductionLine, UUID> {
}
