package io.openadtech.vastlint;

/**
 * gRPC call to {@code vastlint-grpc} failed. The cause is the original
 * {@link io.grpc.StatusRuntimeException}.
 */
public final class VastlintException extends RuntimeException {
    public VastlintException(String message, Throwable cause) {
        super(message, cause);
    }
}
