package com.tradex.unionnorth.organization.mapper;

import com.tradex.unionnorth.organization.domain.Department;
import com.tradex.unionnorth.organization.domain.Location;
import com.tradex.unionnorth.organization.dto.DepartmentResponse;
import org.springframework.stereotype.Component;

@Component
public class DepartmentMapper {

    public DepartmentResponse toResponse(Department department) {
        Location location = department.getLocation();
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.isActive(),
                department.getCompany().getId(),
                department.getCompany().getName(),
                location == null ? null : location.getId(),
                location == null ? null : location.getName());
    }
}
