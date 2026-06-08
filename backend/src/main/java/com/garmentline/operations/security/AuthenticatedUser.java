package com.garmentline.operations.security;

public record AuthenticatedUser(
    String id,
    String fullName,
    String role
) {
}
