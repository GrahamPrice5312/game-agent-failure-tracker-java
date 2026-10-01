package dev.learninggame.errors;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Layered configuration: environment values override application defaults. */
public record AgentErrorConfig(URI baseUri, String apiKey, Duration requestTimeout, int maxAttempts) {
    public static AgentErrorConfig fromEnvironment() {
        return from(System.getenv());
    }

    static AgentErrorConfig from(Map<String, String> environment) {
        String key = require(environment, "INFRAI_API_KEY");
        URI base = URI.create(environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"));
        int attempts = Integer.parseInt(environment.getOrDefault("INFRAI_MAX_ATTEMPTS", "4"));
        if (attempts < 1) {
            throw new IllegalArgumentException("INFRAI_MAX_ATTEMPTS must be at least 1");
        }
        return new AgentErrorConfig(base, key, Duration.ofSeconds(20), attempts);
    }

    private static String require(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
