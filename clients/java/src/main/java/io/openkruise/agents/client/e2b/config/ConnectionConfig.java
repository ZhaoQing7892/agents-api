package io.openkruise.agents.client.e2b.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.openkruise.agents.client.e2b.utils.PathModifyingInterceptor;
import io.openkruise.agents.client.e2b.utils.RpcUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ConnectionConfig {
    public static final int ENVD_PORT = 49983;
    public static final int KEEPALIVE_PING_INTERVAL_SEC = 50;
    public static final String KEEPALIVE_PING_HEADER = "Keepalive-Ping-Interval";
    public static final long DEFAULT_REQUEST_TIMEOUT_MS = 60_000;

    private final String domain;
    private final boolean debug;
    private final String apiKey;
    private final String accessToken;
    private final String apiUrl;
    private final String sandboxUrl;
    private final long requestTimeoutMs;
    private final Map<String, String> headers;
    private final Map<String, String> extraSandboxHeaders;

    public ConnectionConfig() {
        this(new Builder());
    }

    private ConnectionConfig(Builder builder) {
        this.debug = builder.debug != null ? builder.debug : getEnvBool("E2B_DEBUG", false);
        this.domain = builder.domain != null ? builder.domain : getEnvVar("E2B_DOMAIN", "e2b.app");
        this.apiKey = builder.apiKey != null ? builder.apiKey : getEnvVar("E2B_API_KEY", null);
        this.accessToken = builder.accessToken != null ? builder.accessToken : getEnvVar("E2B_ACCESS_TOKEN", null);
        this.apiUrl = builder.apiUrl != null ? builder.apiUrl : getEnvVar("E2B_API_URL", null);
        this.sandboxUrl = builder.sandboxUrl != null ? builder.sandboxUrl : getEnvVar("E2B_SANDBOX_URL", null);
        this.requestTimeoutMs = builder.requestTimeoutMs != null ? builder.requestTimeoutMs
            : DEFAULT_REQUEST_TIMEOUT_MS;
        this.headers = new HashMap<>(builder.headers);
        this.headers.put("User-Agent", "e2b-java-sdk/0.1.0");
        this.extraSandboxHeaders = new HashMap<>(builder.extraSandboxHeaders);
    }

    public static String getDomainFromEnv() {
        String value = System.getenv("E2B_DOMAIN");
        return value != null ? value : "e2b.app";
    }

    public static boolean getDebugFromEnv() {
        String value = System.getenv("E2B_DEBUG");
        return value != null && value.equalsIgnoreCase("true");
    }

    public static String getApiKeyFromEnv() {
        return System.getenv("E2B_API_KEY");
    }

    public static String getAccessTokenFromEnv() {
        return System.getenv("E2B_ACCESS_TOKEN");
    }

    public static String getApiUrlFromEnv() {
        return System.getenv("E2B_API_URL");
    }

    public static String getSandboxUrlFromEnv() {
        return System.getenv("E2B_SANDBOX_URL");
    }

    private String getEnvVar(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    private boolean getEnvBool(String name, boolean defaultValue) {
        String value = System.getenv(name);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    public String getDomain() {
        return domain;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getApiUrl() {
        if (apiUrl != null) {
            return apiUrl;
        }
        return debug ? "http://localhost:3000" : "https://api." + domain;
    }

    public String getSandboxUrl(String sandboxId, String sandboxDomain) {
        if (sandboxUrl != null) {
            return sandboxUrl;
        }
        String protocol = debug ? "http" : "https";
        String host = getSandboxHost(sandboxId, sandboxDomain);
        return protocol + "://" + host;
    }

    public String getSandboxHost(String sandboxId, String sandboxDomain) {
        if (debug) {
            return "localhost";
        }
        return String.format("%d-%s.%s", ENVD_PORT, sandboxId, sandboxDomain != null ? sandboxDomain : domain);
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public long getRequestTimeoutMs(Long overrideTimeoutMs) {
        if (overrideTimeoutMs != null) {
            return overrideTimeoutMs;
        }
        return requestTimeoutMs;
    }

    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }

    public Map<String, String> getSandboxHeaders() {
        Map<String, String> sandboxHeaders = new HashMap<>(headers);
        sandboxHeaders.putAll(extraSandboxHeaders);
        return sandboxHeaders;
    }

    public ManagedChannel createChannel(String sandboxId, String sandboxDomain) {
        String target = getSandboxHost(sandboxId, sandboxDomain);

        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target);

        if (debug) {
            builder.usePlaintext();
            // build path
            builder.intercept(new PathModifyingInterceptor(sandboxId, ENVD_PORT));
        } else {
            builder.useTransportSecurity();
        }

        Metadata metadata = RpcUtils.createFullMetadata(
            sandboxId, ENVD_PORT, apiKey, accessToken, getSandboxHeaders()
        );

        return builder.intercept(MetadataUtils.newAttachHeadersInterceptor(metadata))
            .keepAliveTime(KEEPALIVE_PING_INTERVAL_SEC, TimeUnit.SECONDS)
            .build();
    }

    public static class Builder {
        private String domain;
        private Boolean debug;
        private String apiKey;
        private String accessToken;
        private String apiUrl;
        private String sandboxUrl;
        private Long requestTimeoutMs;
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> extraSandboxHeaders = new HashMap<>();

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public Builder sandboxUrl(String sandboxUrl) {
            this.sandboxUrl = sandboxUrl;
            return this;
        }

        public Builder requestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
            return this;
        }

        public Builder addHeader(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public Builder extraSandboxHeaders(Map<String, String> extraSandboxHeaders) {
            this.extraSandboxHeaders = extraSandboxHeaders != null ? new HashMap<>(extraSandboxHeaders)
                : new HashMap<>();
            return this;
        }

        public Builder addExtraSandboxHeader(String key, String value) {
            this.extraSandboxHeaders.put(key, value);
            return this;
        }

        public ConnectionConfig build() {
            return new ConnectionConfig(this);
        }
    }
}
