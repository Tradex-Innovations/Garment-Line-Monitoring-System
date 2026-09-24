package com.tradex.unionnorth.employee.dto;

import com.tradex.unionnorth.employee.domain.CadreStatus;

public record EmployeeSearchCriteria(CadreStatus status, String search) {
}
