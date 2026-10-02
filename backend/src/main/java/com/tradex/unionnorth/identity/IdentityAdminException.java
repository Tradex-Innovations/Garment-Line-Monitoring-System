package com.tradex.unionnorth.identity;

import com.tradex.unionnorth.common.error.BusinessException;
import org.springframework.http.HttpStatus;

public class IdentityAdminException extends BusinessException {

    public IdentityAdminException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
