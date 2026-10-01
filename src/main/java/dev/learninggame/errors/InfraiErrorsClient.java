package dev.learninggame.errors;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/** Small reusable module for the one Infrai capability used by this example. */
public final class InfraiErrorsClient {
    @FunctionalInterface
    public interface Transport {
        Reply send(HttpRequest request) throws IOException, InterruptedException;
    }

    public record Reply(int statusCode, String body, Optional<String> retryAfter) {}

    private final AgentErrorConfig config;
    private final Transport transport;

    public InfraiErrorsClient(AgentErrorConfig config) {
        this(config, jdkTransport(config.requestTimeout()));
    }

    InfraiErrorsClient(AgentErrorConfig config, Transport transport) {
        this.config = config;
        this.transport = transport;
    }

    /** infrai.errors.capture -> POST /v1/errors/capture */
    public void capture(String exceptionPayload, String idempotencyKey) {
        String json = "{\"exception\":\"" + escape(exceptionPayload) + "\"}";
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve("/v1/errors/capture"))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            Reply reply = send(request);

            Envelope envelope = Envelope.decode(reply.body()); // Decode before status decisions.
            if (reply.statusCode() == 429) {
                if (attempt < config.maxAttempts()) {
                    sleep(backoff(reply.retryAfter(), attempt));
                    continue;
                }
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), reply.statusCode());
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), reply.statusCode());
            }
            if (reply.statusCode() >= 500) {
                throw new InfraiException("TRANSPORT_RESPONSE", "Infrai request was not accepted", reply.statusCode());
            }
            return;
        }
    }

    private Reply send(HttpRequest request) {
        try {
            return transport.send(request);
        } catch (IOException e) {
            throw new InfraiException("TRANSPORT_IO", e.getMessage(), 502);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("TRANSPORT_INTERRUPTED", e.getMessage(), 502);
        }
    }

    private static Transport jdkTransport(Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        return request -> {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body(), response.headers().firstValue("Retry-After"));
        };
    }

    private static Duration backoff(Optional<String> retryAfter, int attempt) {
        if (retryAfter.isPresent()) {
            try {
                return Duration.ofSeconds(Math.max(0, Long.parseLong(retryAfter.get())));
            } catch (NumberFormatException ignored) {
                // An HTTP-date still receives bounded exponential backoff.
            }
        }
        return Duration.ofMillis(Math.min(8_000L, 250L * (1L << Math.min(attempt - 1, 5))));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("BACKOFF_INTERRUPTED", e.getMessage(), 502);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private record Envelope(boolean ok, String errorCode, String errorMessage) {
        static Envelope decode(String json) {
            if (json == null || json.isBlank()) {
                throw new InfraiException("INVALID_ENVELOPE", "Response envelope is empty", 502);
            }
            Boolean ok = booleanField(json, "ok");
            if (ok == null) {
                throw new InfraiException("INVALID_ENVELOPE", "Response envelope has no ok field", 502);
            }
            return new Envelope(ok, stringField(json, "code"), stringField(json, "message"));
        }

        private static Boolean booleanField(String json, String name) {
            int colon = fieldColon(json, name);
            if (colon < 0) return null;
            String tail = json.substring(colon + 1).stripLeading().toLowerCase(Locale.ROOT);
            if (tail.startsWith("true")) return true;
            if (tail.startsWith("false")) return false;
            return null;
        }

        private static String stringField(String json, String name) {
            int colon = fieldColon(json, name);
            if (colon < 0) return "";
            int start = json.indexOf('\"', colon + 1);
            if (start < 0) return "";
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            for (int i = start + 1; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (escaped) {
                    value.append(ch == 'n' ? '\n' : ch);
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '\"') {
                    return value.toString();
                } else {
                    value.append(ch);
                }
            }
            return "";
        }

        private static int fieldColon(String json, String name) {
            int field = json.indexOf("\"" + name + "\"");
            return field < 0 ? -1 : json.indexOf(':', field + name.length() + 2);
        }
    }
}
