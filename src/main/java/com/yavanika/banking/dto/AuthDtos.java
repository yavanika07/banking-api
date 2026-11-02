package com.yavanika.banking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "fullName is required") @Size(max = 100) String fullName,
            @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
            @NotBlank(message = "password is required")
            @Size(min = 8, max = 64, message = "password must be 8-64 characters") String password
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "email is required") String email,
            @NotBlank(message = "password is required") String password
    ) {
    }

    public record AuthResponse(String token, String tokenType, long expiresInSeconds,
                               Long customerId, String fullName, String email) {
    }
}
