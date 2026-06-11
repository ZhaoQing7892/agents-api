package io.openkruise.agents.client.e2b;

import io.openkruise.agents.client.runtime.RuntimeConfig;

import java.util.Map;

/**
 * RuntimeConfig subclass that carries E2B-specific URL construction and request header logic.
 */
public class E2bRuntimeConfig extends RuntimeConfig {

    private final String sandboxBaseURL;
    private final int e2bRuntimePort;

    private E2bRuntimeConfig(Builder builder) {
        super(builder);
        this.sandboxBaseURL = builder.sandboxBaseURL;
        this.e2bRuntimePort = builder.e2bRuntimePort;
    }

    /**
     * PRIVATE mode uses sandboxBaseURL, NATIVE mode uses {@code <scheme>://<port>-<sandboxID>.<domain>}.
     */
    @Override
    public String getSandboxURL(String sandboxID) {
        if (sandboxBaseURL != null && !sandboxBaseURL.isEmpty()) {
            // PRIVATE mode: sandboxBaseURL already contains full path prefix
            return sandboxBaseURL;
        }
        return String.format("%s://%d-%s.%s", getScheme(), e2bRuntimePort, sandboxID, getDomain());
    }

    /** Adds e2b-sandbox-port to the base request headers. */
    @Override
    public Map<String, String> getSandboxHeaders(String sandboxID) {
        Map<String, String> result = super.getSandboxHeaders(sandboxID);
        result.put("e2b-sandbox-port", String.valueOf(e2bRuntimePort));
        return result;
    }

    public static E2bRuntimeConfig fromConnectionConfig(ConnectionConfig connectionConfig, String envdAccessToken) {
        Builder builder = new Builder();
        builder.domain(connectionConfig.getDomain());
        builder.scheme(connectionConfig.getScheme());
        builder.requestTimeoutMs(connectionConfig.getRequestTimeoutMs());

        if (connectionConfig.getApiKey() != null) {
            builder.apiKey(connectionConfig.getApiKey());
        }
        if (connectionConfig.getHeaders() != null) {
            builder.headers(connectionConfig.getHeaders());
        }
        if (envdAccessToken != null && !envdAccessToken.isEmpty()) {
            builder.runtimeToken(envdAccessToken);
        }

        builder.e2bRuntimePort(connectionConfig.getPort());

        if (connectionConfig.getProtocol() == ConnectionConfig.Protocol.PRIVATE) {
            String scheme = connectionConfig.getScheme() != null ? connectionConfig.getScheme() : "https";
            builder.e2bSandboxBaseURL(String.format("%s://%s", scheme, connectionConfig.getDomain()));
        }

        return builder.buildE2b();
    }

    public static class Builder extends RuntimeConfig.Builder {
        private String sandboxBaseURL;
        private int e2bRuntimePort = ConnectionConfig.DEFAULT_RUNTIME_PORT;

        public Builder() {
            super();
        }

        /**
         * Sets sandboxBaseURL for PRIVATE mode.
         */
        public Builder e2bSandboxBaseURL(String sandboxBaseURL) {
            this.sandboxBaseURL = sandboxBaseURL;
            return this;
        }

        public Builder e2bRuntimePort(int port) {
            this.e2bRuntimePort = port;
            return this;
        }

        public E2bRuntimeConfig buildE2b() {
            return new E2bRuntimeConfig(this);
        }

        @Override
        public Builder domain(String domain) {
            super.domain(domain);
            return this;
        }

        @Override
        public Builder scheme(String scheme) {
            super.scheme(scheme);
            return this;
        }

        @Override
        public Builder runtimeToken(String runtimeToken) {
            super.runtimeToken(runtimeToken);
            return this;
        }

        @Override
        public Builder authHeader(String authHeader) {
            super.authHeader(authHeader);
            return this;
        }

        @Override
        public Builder apiKey(String apiKey) {
            super.apiKey(apiKey);
            return this;
        }

        @Override
        public Builder headers(Map<String, String> headers) {
            super.headers(headers);
            return this;
        }

        @Override
        public Builder addHeader(String key, String value) {
            super.addHeader(key, value);
            return this;
        }

        @Override
        public Builder requestTimeoutMs(long requestTimeoutMs) {
            super.requestTimeoutMs(requestTimeoutMs);
            return this;
        }

        @Override
        public Builder runtimeUrl(String runtimeUrl) {
            super.runtimeUrl(runtimeUrl);
            return this;
        }
    }
}
