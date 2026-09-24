package com.tradex.unionnorth.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeUserStatusRequest(
        @NotNull UserAccountStatus status,
        @Size(max = 500) String reason) {
}
