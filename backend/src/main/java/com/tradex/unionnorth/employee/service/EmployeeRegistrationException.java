package com.tradex.unionnorth.employee.service;

import com.tradex.unionnorth.common.error.BusinessException;
import org.springframework.http.HttpStatus;

public class EmployeeRegistrationException extends BusinessException {
    public EmployeeRegistrationException(String message) {
        super("EMPLOYEE_REGISTRATION_INVALID", message, HttpStatus.BAD_REQUEST);
    }
}
