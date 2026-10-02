package com.tradex.unionnorth.employee.service;

import com.tradex.unionnorth.common.error.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class EmployeeNotFoundException extends BusinessException {

    public EmployeeNotFoundException(UUID id) {
        super("EMPLOYEE_NOT_FOUND", "Employee not found: " + id, HttpStatus.NOT_FOUND);
    }
}
