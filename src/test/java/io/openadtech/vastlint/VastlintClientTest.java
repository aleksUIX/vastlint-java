package io.openadtech.vastlint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.openadtech.vastlint.v1.FixRequest;
import io.openadtech.vastlint.v1.FixResponse;
import io.openadtech.vastlint.v1.ListRulesRequest;
import io.openadtech.vastlint.v1.ListRulesResponse;
import io.openadtech.vastlint.v1.RuleLevel;
import io.openadtech.vastlint.v1.RuleMeta;
import io.openadtech.vastlint.v1.ValidateRequest;
import io.openadtech.vastlint.v1.ValidateResponse;
import io.openadtech.vastlint.v1.VastlintServiceGrpc;
import io.openadtech.vastlint.v1.Verdict;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VastlintClientTest {

    private String serverName;
    private Server server;
    private ManagedChannel channel;
    private VastlintClient client;
    private FakeService fake;

    @BeforeEach
    void setUp() throws IOException {
        serverName = InProcessServerBuilder.generateName();
        fake = new FakeService();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(fake)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = VastlintClient.fromChannel(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        client.close();
        channel.shutdownNow();
        server.shutdownNow();
        channel.awaitTermination(2, TimeUnit.SECONDS);
        server.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void validateRejectsEmptyDocument() {
        assertThrows(IllegalArgumentException.class, () -> client.validate(""));
        assertThrows(IllegalArgumentException.class, () -> client.validate(null));
    }

    @Test
    void validateReturnsVerdict() {
        fake.valid = true;
        Verdict verdict = client.validate("<VAST version=\"4.2\"></VAST>");
        assertTrue(verdict.getValid());
        assertEquals("<VAST version=\"4.2\"></VAST>", fake.lastDocument);
    }

    @Test
    void validateSendsOptions() {
        client.validate(
                "<VAST version=\"4.2\"></VAST>",
                VastlintClient.Options.defaults()
                        .wrapperDepth(2)
                        .maxWrapperDepth(4)
                        .override("VAST-4.1-mezzanine-recommended", "off"));
        assertEquals(2, fake.lastRequest.getContext().getWrapperDepth());
        assertEquals(4, fake.lastRequest.getContext().getMaxWrapperDepth());
        assertEquals(
                RuleLevel.RULE_LEVEL_OFF,
                fake.lastRequest.getContext().getRuleOverridesMap().get("VAST-4.1-mezzanine-recommended"));
    }

    @Test
    void listRulesReturnsCatalog() {
        ListRulesResponse response = client.listRules();
        assertEquals(1, response.getRulesCount());
        assertEquals("VAST-2.0-root-version", response.getRules(0).getRuleId());
    }

    @Test
    void validateWrapsServerFailure() {
        fake.fail = true;
        assertThrows(VastlintException.class, () -> client.validate("<VAST></VAST>"));
    }

    private static final class FakeService extends VastlintServiceGrpc.VastlintServiceImplBase {
        boolean valid;
        boolean fail;
        String lastDocument;
        ValidateRequest lastRequest;

        @Override
        public void validate(ValidateRequest request, StreamObserver<ValidateResponse> observer) {
            lastRequest = request;
            lastDocument = request.getDocument();
            if (fail) {
                observer.onError(Status.RESOURCE_EXHAUSTED.asRuntimeException());
                return;
            }
            observer.onNext(ValidateResponse.newBuilder()
                    .setVerdict(Verdict.newBuilder().setValid(valid).build())
                    .build());
            observer.onCompleted();
        }

        @Override
        public void fix(FixRequest request, StreamObserver<FixResponse> observer) {
            observer.onNext(FixResponse.newBuilder().setDocument(request.getDocument()).build());
            observer.onCompleted();
        }

        @Override
        public void listRules(ListRulesRequest request, StreamObserver<ListRulesResponse> observer) {
            observer.onNext(ListRulesResponse.newBuilder()
                    .addRules(RuleMeta.newBuilder().setRuleId("VAST-2.0-root-version").build())
                    .build());
            observer.onCompleted();
        }
    }
}
