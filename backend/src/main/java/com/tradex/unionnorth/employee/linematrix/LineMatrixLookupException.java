package com.tradex.unionnorth.employee.linematrix;

import com.tradex.unionnorth.common.error.BusinessException;
import org.springframework.http.HttpStatus;

public class LineMatrixLookupException extends BusinessException {
    public LineMatrixLookupException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
