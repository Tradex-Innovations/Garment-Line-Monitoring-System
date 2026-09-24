package com.tradex.unionnorth.identity.dto;

import java.util.List;

public record UserAccountPage(
        List<UserAccountResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
}
