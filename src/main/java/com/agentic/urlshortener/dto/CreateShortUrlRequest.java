package com.agentic.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateShortUrlRequest(
        @NotBlank(message = "URL is required")
        @Pattern(regexp = "^(https?://).+", message = "URL must start with http:// or https://")
        String url,

        @Size(max = 20, message = "Alias length must be 20 characters or less")
        String alias
) {}
