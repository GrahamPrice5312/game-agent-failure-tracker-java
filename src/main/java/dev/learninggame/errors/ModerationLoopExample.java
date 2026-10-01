package dev.learninggame.errors;

/** Explanatory entry point: wire the layers as a Spring application would. */
public final class ModerationLoopExample {
    private ModerationLoopExample() {}

    public static void main(String[] args) {
        AgentErrorConfig config = AgentErrorConfig.fromEnvironment();
        InfraiErrorsClient client = new InfraiErrorsClient(config);
        GameAgentFailureService service = new GameAgentFailureService(client);
        AgentFailureController controller = new AgentFailureController(service);

        AgentFailure failure = new AgentFailure(
                "player-asset-1842",
                "creator-cup-final",
                "ugc-safety-review",
                "classify-uploaded-banner",
                new IllegalStateException("moderation model returned no decision"));

        AgentFailureController.ControllerResponse response = controller.capture(failure);
        System.out.println("status=" + response.status()
                + " state=" + response.state()
                + " group=" + response.detail());
    }
}
