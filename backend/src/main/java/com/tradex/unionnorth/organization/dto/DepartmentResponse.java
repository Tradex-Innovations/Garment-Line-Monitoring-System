package com.tradex.unionnorth.organization.dto;

import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        UUID companyId,
        String companyName,
        UUID locationId,
        String locationName) {
}
