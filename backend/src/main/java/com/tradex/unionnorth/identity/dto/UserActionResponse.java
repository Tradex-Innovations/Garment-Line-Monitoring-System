package com.tradex.unionnorth.identity.dto;

public record UserActionResponse(
        UserAccountResponse user,
        boolean emailSent,
        String message) {
}
