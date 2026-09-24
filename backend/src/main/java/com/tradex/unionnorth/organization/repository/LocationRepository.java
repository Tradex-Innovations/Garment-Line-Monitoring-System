package com.tradex.unionnorth.organization.repository;

import com.tradex.unionnorth.organization.domain.Location;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {
}
