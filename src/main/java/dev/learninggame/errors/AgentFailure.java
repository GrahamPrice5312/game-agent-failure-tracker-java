package dev.learninggame.errors;

/** The four coordinates an operator needs to find a failure in the game backend. */
public record AgentFailure(
        String assetId,
        String liveEventId,
        String moderationQueue,
        String agentStep,
        Throwable cause) {

    public AgentFailure {
        require(assetId, "assetId");
        require(liveEventId, "liveEventId");
        require(moderationQueue, "moderationQueue");
        require(agentStep, "agentStep");
        if (cause == null) throw new IllegalArgumentException("cause is required");
    }

    String exceptionPayload() {
        return "type=" + cause.getClass().getName()
                + "; message=" + String.valueOf(cause.getMessage())
                + "; asset=" + assetId
                + "; live_event=" + liveEventId
                + "; moderation_queue=" + moderationQueue
                + "; agent_step=" + agentStep;
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
