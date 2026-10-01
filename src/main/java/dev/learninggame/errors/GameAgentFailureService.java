package dev.learninggame.errors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Application service: turns a game-domain failure into one stable capture. */
public final class GameAgentFailureService {
    private final InfraiErrorsClient errors;

    public GameAgentFailureService(InfraiErrorsClient errors) {
        this.errors = errors;
    }

    public CaptureReceipt track(AgentFailure failure) {
        String groupingKey = String.join(":",
                failure.assetId(), failure.liveEventId(), failure.moderationQueue(), failure.agentStep());
        String idempotencyKey = "game-agent-failure-" + sha256(groupingKey + ":" + failure.exceptionPayload());
        errors.capture(failure.exceptionPayload(), idempotencyKey);
        return new CaptureReceipt(groupingKey, idempotencyKey, "captured");
    }

    public record CaptureReceipt(String groupingKey, String idempotencyKey, String state) {}

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
