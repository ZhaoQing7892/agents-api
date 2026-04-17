package io.openkruise.agents.client.e2b.utils;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import io.openkruise.agents.client.e2b.exceptions.AuthenticationException;
import io.openkruise.agents.client.e2b.exceptions.InvalidArgumentException;
import io.openkruise.agents.client.e2b.exceptions.NotFoundException;
import io.openkruise.agents.client.e2b.exceptions.SandboxException;
import io.openkruise.agents.client.e2b.exceptions.TimeoutException;

import java.util.Map;

public class RpcUtils {
    public static Metadata createAuthMetadata(String apiKey, String envdAccessToken) {
        Metadata metadata = new Metadata();

        // 添加 API Key
        if (apiKey != null && !apiKey.isEmpty()) {
            Metadata.Key<String> apiKeyHeader = Metadata.Key.of("X-API-Key", Metadata.ASCII_STRING_MARSHALLER);
            metadata.put(apiKeyHeader, apiKey);
        }

        // 添加 Access Token（用于 envd 通信）
        if (envdAccessToken != null && !envdAccessToken.isEmpty()) {
            Metadata.Key<String> tokenHeader = Metadata.Key.of("X-Access-Token", Metadata.ASCII_STRING_MARSHALLER);
            metadata.put(tokenHeader, envdAccessToken);
        }

        // 添加 Authorization
        Metadata.Key<String> authorizationHeader = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(authorizationHeader, "Basic cm9vdDo=");

        return metadata;
    }

    public static Metadata createSandboxMetadata(String sandboxId, int envdPort) {
        Metadata metadata = new Metadata();

        Metadata.Key<String> sandboxIdHeader = Metadata.Key.of("E2b-Sandbox-Id", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(sandboxIdHeader, sandboxId);

        Metadata.Key<String> sandboxPortHeader = Metadata.Key.of("E2b-Sandbox-Port", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(sandboxPortHeader, String.valueOf(envdPort));

        return metadata;
    }

    public static Metadata createCustomHeadersMetadata(Map<String, String> headers) {
        Metadata metadata = new Metadata();
        if (headers != null) {
            headers.forEach((key, value) ->
                metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
            );
        }
        return metadata;
    }

    public static Metadata mergeMetadata(Metadata... metadataList) {
        Metadata merged = new Metadata();
        for (Metadata metadata : metadataList) {
            if (metadata != null) {
                merged.merge(metadata);
            }
        }
        return merged;
    }

    public static Metadata createFullMetadata(String sandboxId, int envdPort,
        String apiKey, String accessToken,
        Map<String, String> customHeaders) {
        Metadata authMetadata = createAuthMetadata(apiKey, accessToken);
        Metadata sandboxMetadata = createSandboxMetadata(sandboxId, envdPort);
        Metadata customMetadata = createCustomHeadersMetadata(customHeaders);

        return mergeMetadata(authMetadata, sandboxMetadata, customMetadata);
    }

    public static Metadata createKeepaliveMetadata(int intervalSeconds) {
        Metadata metadata = new Metadata();
        Metadata.Key<String> keepaliveHeader = Metadata.Key.of("Keepalive-Ping", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(keepaliveHeader, String.valueOf(intervalSeconds));
        return metadata;
    }

    public static SandboxException handleRpcException(StatusRuntimeException e) {
        Status status = e.getStatus();

        switch (status.getCode()) {
            case INVALID_ARGUMENT:
                return new InvalidArgumentException(status.getDescription());

            case UNAUTHENTICATED:
                return new AuthenticationException(status.getDescription());

            case NOT_FOUND:
                return new NotFoundException(status.getDescription());

            case UNAVAILABLE:
                return new SandboxException("Sandbox unavailable: " + status.getDescription());

            case CANCELLED:
                return new TimeoutException(
                    status.getDescription() + ": This error is likely due to exceeding 'requestTimeoutMs'. " +
                        "You can pass the request timeout value as an option when making the request."
                );

            case DEADLINE_EXCEEDED:
                return new TimeoutException(
                    status.getDescription() + ": This error is likely due to exceeding 'timeoutMs' — " +
                        "the total time a long running request (like command execution or directory watch) can be "
                        + "active. "
                        +
                        "It can be modified by passing 'timeoutMs' when making the request. Use '0' to disable the "
                        + "timeout."
                );

            default:
                return new SandboxException(status.getCode() + ": " + status.getDescription());
        }
    }

    public static boolean isNotFoundError(StatusRuntimeException e) {
        return e.getStatus().getCode() == Status.Code.NOT_FOUND;
    }
}
