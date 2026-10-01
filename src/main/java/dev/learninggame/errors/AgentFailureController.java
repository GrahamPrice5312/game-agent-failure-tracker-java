package dev.learninggame.errors;

/** HTTP-adapter-shaped boundary showing how envelope errors map to caller responses. */
public final class AgentFailureController {
    private final GameAgentFailureService service;

    public AgentFailureController(GameAgentFailureService service) {
        this.service = service;
    }

    public ControllerResponse capture(AgentFailure failure) {
        try {
            GameAgentFailureService.CaptureReceipt receipt = service.track(failure);
            return new ControllerResponse(202, receipt.state(), receipt.groupingKey());
        } catch (InfraiException error) {
            int status = error.httpStatus() >= 400 && error.httpStatus() < 500
                    ? error.httpStatus() : 502;
            return new ControllerResponse(status, "rejected", error.code());
        }
    }

    public record ControllerResponse(int status, String state, String detail) {}
}
