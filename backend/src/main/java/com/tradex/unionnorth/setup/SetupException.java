package com.tradex.unionnorth.setup;

import com.tradex.unionnorth.common.error.BusinessException;

import org.springframework.http.HttpStatus;

public class SetupException extends BusinessException {
    public SetupException(String message) {
        super("SETUP_VALIDATION", message, HttpStatus.BAD_REQUEST);
    }

    public SetupException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static SetupException conflict() {
        return new SetupException(
                "SETUP_CONFLICT",
                "This record changed. Reload before saving again.",
                HttpStatus.CONFLICT);
    }

    public static SetupException missing() {
        return new SetupException(
                "SETUP_NOT_FOUND",
                "The requested setup record was not found.",
                HttpStatus.NOT_FOUND);
    }
}
