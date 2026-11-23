package com.portafolio.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Refresh refresh, PasswordReset passwordReset, RateLimit rateLimit,
                            Mail mail, Cors cors, Seed seed) {

    public record Jwt(String secret, String issuer, String audience, long accessTokenMinutes) {}

    public record Refresh(long ttlDays, String cookieName, boolean cookieSecure) {}

    public record PasswordReset(long ttlMinutes, String frontendUrl) {}

    public record RateLimit(int maxLoginFailures, long windowMinutes, int maxResetRequests) {}

    public record Mail(boolean devOutbox) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Seed(boolean enabled, String demoPassword) {}
}
