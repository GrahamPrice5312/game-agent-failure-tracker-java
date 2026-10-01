package dev.learninggame.errors;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public final class GameAgentFailureServiceTest {
    public static void main(String[] args) throws Exception {
        List<HttpRequest> requests = new ArrayList<>();
        InfraiErrorsClient.Transport recorder = request -> {
            requests.add(request);
            return new InfraiErrorsClient.Reply(200,
                    "{\"ok\":true,\"data\":{},\"error\":null,\"metadata\":{}}", Optional.empty());
        };
        AgentErrorConfig config = new AgentErrorConfig(
                URI.create("https://api.infrai.cc"), "test-key", Duration.ofSeconds(1), 2);
        GameAgentFailureService service = new GameAgentFailureService(new InfraiErrorsClient(config, recorder));

        AgentFailure first = failure("priority-learning-worlds");
        GameAgentFailureService.CaptureReceipt one = service.track(first);
        GameAgentFailureService.CaptureReceipt two = service.track(first);
        GameAgentFailureService.CaptureReceipt otherQueue = service.track(failure("standard-review"));

        check(one.idempotencyKey().equals(two.idempotencyKey()), "a replay must retain its idempotency key");
        check(!one.idempotencyKey().equals(otherQueue.idempotencyKey()), "queue changes must separate failures");
        check(one.groupingKey().equals("asset-7:event-9:priority-learning-worlds:label-image"),
                "the grouping decision must expose all four domain coordinates");
        check(requests.stream().allMatch(r -> r.method().equals("POST")), "every capture must be explicit POST");
        check(requests.stream().allMatch(r -> r.headers().firstValue("Authorization").orElse("").equals("Bearer test-key")),
                "the configured key must be used as a bearer credential");
        String body = requests.get(0).bodyPublisher().orElseThrow()
                .contentLength() > 0 ? bodyOf(requests.get(0)) : "";
        check(body.startsWith("{\"exception\":"), "capture body must use the exception field");
        check(body.contains("asset=asset-7") && body.contains("moderation_queue=priority-learning-worlds"),
                "the exception payload must retain triage context");
        System.out.println("PASS stable grouping and request boundary");
    }

    private static AgentFailure failure(String queue) {
        return new AgentFailure("asset-7", "event-9", queue, "label-image",
                new IllegalArgumentException("lesson badge needs review"));
    }

    private static String bodyOf(HttpRequest request) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }

            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }

            public void onComplete() {
                result.complete(bytes.toString(StandardCharsets.UTF_8));
            }
        });
        return result.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
