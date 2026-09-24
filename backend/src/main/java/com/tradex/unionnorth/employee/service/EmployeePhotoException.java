package com.tradex.unionnorth.employee.service;

import com.tradex.unionnorth.common.error.BusinessException;
import org.springframework.http.HttpStatus;

public class EmployeePhotoException extends BusinessException {

    public EmployeePhotoException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
