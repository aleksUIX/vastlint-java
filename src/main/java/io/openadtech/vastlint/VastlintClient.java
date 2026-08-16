package io.openadtech.vastlint;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.openadtech.vastlint.v1.FixRequest;
import io.openadtech.vastlint.v1.FixResponse;
import io.openadtech.vastlint.v1.ListRulesRequest;
import io.openadtech.vastlint.v1.ListRulesResponse;
import io.openadtech.vastlint.v1.RuleLevel;
import io.openadtech.vastlint.v1.RuleSource;
import io.openadtech.vastlint.v1.ValidateRequest;
import io.openadtech.vastlint.v1.ValidationContext;
import io.openadtech.vastlint.v1.VastVersion;
import io.openadtech.vastlint.v1.VastlintServiceGrpc;
import io.openadtech.vastlint.v1.Verdict;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Blocking gRPC client for {@code vastlint-grpc}.
 *
 * <p>This is the JVM counterpart of {@code vastlint-go}: same catalog, same rule
 * IDs. Go embeds the Rust core in-process. Java talks to the server over
 * {@code openadtech.vastlint.v1} so a Vert.x or Netty event loop never loads a
 * JNI library.
 *
 * <pre>{@code
 * try (VastlintClient client = VastlintClient.connect("localhost:50051")) {
 *     Verdict verdict = client.validate(xml);
 *     if (!verdict.getValid()) {
 *         // reject the bid
 *     }
 * }
 * }</pre>
 */
public final class VastlintClient implements AutoCloseable {

    private static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(5);

    private final ManagedChannel channel;
    private final VastlintServiceGrpc.VastlintServiceBlockingStub stub;
    private final boolean ownsChannel;

    private VastlintClient(ManagedChannel channel, Duration deadline, boolean ownsChannel) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.ownsChannel = ownsChannel;
        Duration resolved = deadline == null ? DEFAULT_DEADLINE : deadline;
        this.stub = VastlintServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(resolved.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Plaintext connection. Matches the local {@code vastlint-grpc} default and
     * the {@code grpcurl -plaintext} examples.
     */
    public static VastlintClient connect(String target) {
        return connect(target, DEFAULT_DEADLINE);
    }

    public static VastlintClient connect(String target, Duration deadline) {
        Objects.requireNonNull(target, "target");
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        return new VastlintClient(channel, deadline, true);
    }

    /** TLS connection. Use this against a load balancer that terminates TLS. */
    public static VastlintClient connectTls(String target) {
        return connectTls(target, DEFAULT_DEADLINE);
    }

    public static VastlintClient connectTls(String target, Duration deadline) {
        Objects.requireNonNull(target, "target");
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).build();
        return new VastlintClient(channel, deadline, true);
    }

    /**
     * Wrap an existing channel. The caller owns shutdown. Used by tests with an
     * in-process server.
     */
    public static VastlintClient fromChannel(ManagedChannel channel) {
        return fromChannel(channel, DEFAULT_DEADLINE);
    }

    public static VastlintClient fromChannel(ManagedChannel channel, Duration deadline) {
        return new VastlintClient(channel, deadline, false);
    }

    /** Validate a document with server defaults. */
    public Verdict validate(String document) {
        return validate(document, Options.defaults());
    }

    public Verdict validate(String document, Options options) {
        requireDocument(document);
        try {
            return stub.validate(ValidateRequest.newBuilder()
                            .setDocument(document)
                            .setContext(options.toContext())
                            .build())
                    .getVerdict();
        } catch (StatusRuntimeException e) {
            throw new VastlintException("validate failed: " + e.getStatus(), e);
        }
    }

    public FixResponse fix(String document) {
        return fix(document, Options.defaults());
    }

    public FixResponse fix(String document, Options options) {
        requireDocument(document);
        try {
            return stub.fix(FixRequest.newBuilder()
                    .setDocument(document)
                    .setContext(options.toContext())
                    .build());
        } catch (StatusRuntimeException e) {
            throw new VastlintException("fix failed: " + e.getStatus(), e);
        }
    }

    public ListRulesResponse listRules() {
        return listRules(ListRulesRequest.getDefaultInstance());
    }

    public ListRulesResponse listRules(RuleSource... sources) {
        ListRulesRequest.Builder builder = ListRulesRequest.newBuilder();
        if (sources != null) {
            for (RuleSource source : sources) {
                if (source != null) {
                    builder.addSources(source);
                }
            }
        }
        return listRules(builder.build());
    }

    public ListRulesResponse listRules(ListRulesRequest request) {
        try {
            return stub.listRules(request == null ? ListRulesRequest.getDefaultInstance() : request);
        } catch (StatusRuntimeException e) {
            throw new VastlintException("listRules failed: " + e.getStatus(), e);
        }
    }

    @Override
    public void close() {
        if (!ownsChannel) {
            return;
        }
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void requireDocument(String document) {
        if (document == null || document.isEmpty()) {
            throw new IllegalArgumentException("vastlint: document must not be empty");
        }
    }

    /**
     * Per-call validation settings. A zero-value {@code Options} is equivalent
     * to calling {@link #validate(String)}.
     */
    public static final class Options {
        private int wrapperDepth;
        private int maxWrapperDepth;
        private VastVersion forcedVersion = VastVersion.VAST_VERSION_UNSPECIFIED;
        private final Map<String, RuleLevel> ruleOverrides = new LinkedHashMap<>();

        public static Options defaults() {
            return new Options();
        }

        public Options wrapperDepth(int wrapperDepth) {
            this.wrapperDepth = wrapperDepth;
            return this;
        }

        public Options maxWrapperDepth(int maxWrapperDepth) {
            this.maxWrapperDepth = maxWrapperDepth;
            return this;
        }

        public Options forcedVersion(VastVersion forcedVersion) {
            this.forcedVersion = forcedVersion == null
                    ? VastVersion.VAST_VERSION_UNSPECIFIED
                    : forcedVersion;
            return this;
        }

        public Options override(String ruleId, RuleLevel level) {
            if (ruleId == null || ruleId.isEmpty()) {
                throw new IllegalArgumentException("vastlint: rule id must not be empty");
            }
            if (level == null || level == RuleLevel.RULE_LEVEL_UNSPECIFIED) {
                throw new IllegalArgumentException("vastlint: rule level must be set");
            }
            ruleOverrides.put(ruleId, level);
            return this;
        }

        /**
         * Accepts the same severity strings as {@code vastlint-go}:
         * {@code error}, {@code warning}, {@code info}, {@code off}.
         */
        public Options override(String ruleId, String level) {
            return override(ruleId, parseLevel(level));
        }

        ValidationContext toContext() {
            ValidationContext.Builder builder = ValidationContext.newBuilder()
                    .setWrapperDepth(wrapperDepth)
                    .setMaxWrapperDepth(maxWrapperDepth)
                    .setForcedVersion(forcedVersion);
            builder.putAllRuleOverrides(ruleOverrides);
            return builder.build();
        }

        private static RuleLevel parseLevel(String level) {
            if (level == null) {
                throw new IllegalArgumentException("vastlint: rule level must not be empty");
            }
            switch (level.toLowerCase(Locale.ROOT)) {
                case "error":
                    return RuleLevel.RULE_LEVEL_ERROR;
                case "warning":
                    return RuleLevel.RULE_LEVEL_WARNING;
                case "info":
                    return RuleLevel.RULE_LEVEL_INFO;
                case "off":
                    return RuleLevel.RULE_LEVEL_OFF;
                default:
                    throw new IllegalArgumentException(
                            "vastlint: unrecognised rule level '" + level
                                    + "' (use error, warning, info, off)");
            }
        }
    }
}
