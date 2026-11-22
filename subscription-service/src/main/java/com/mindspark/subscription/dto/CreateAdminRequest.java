package com.mindspark.subscription.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Temporary password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String temporaryPassword
) {
    // Override toString so password is NEVER printed in logs or exception traces
    @Override
    public String toString() {
        return "CreateAdminRequest[email=" + email + ", firstName=" + firstName + ", lastName=" + lastName + ", temporaryPassword=***]";
    }
}
