package io.openkruise.agents.client.e2b;

import io.openkruise.agents.client.e2b.api.SandboxesApi;
import io.openkruise.agents.client.e2b.api.invoker.ApiClient;

import java.util.HashMap;
import java.util.Map;

/**
 * E2B control plane connection configuration with Builder pattern support.
 */
public class ConnectionConfig {

    public enum Protocol {
        NATIVE("native"),
        PRIVATE("private");

        private final String value;

        Protocol(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private static final String DEFAULT_DOMAIN = "your.domain.com";
    private static final String DEFAULT_SCHEME = "https";
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 60_000L;
    static final int DEFAULT_SANDBOX_TIMEOUT = 300;
    static final int DEFAULT_RUNTIME_PORT = 49983;

    private String apiKey;
    private String accessToken;
    private String domain;
    private String scheme;
    private Protocol protocol;
    private String apiURL;
    private String sandboxBaseURL;
    private boolean debug;
    private long requestTimeoutMs;
    private int port;
    private Map<String, String> headers;

    private volatile ApiClient apiClient;
    private volatile SandboxesApi sandboxesApi;
    private final Object lock = new Object();

    private ConnectionConfig() {
        this.domain = DEFAULT_DOMAIN;
        this.scheme = DEFAULT_SCHEME;
        this.protocol = Protocol.NATIVE;
        this.requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
        this.port = DEFAULT_RUNTIME_PORT;
        this.headers = new HashMap<>();
    }

    private ConnectionConfig(ConnectionConfig config) {
        this.apiKey = config.apiKey;
        this.accessToken = config.accessToken;
        this.domain = config.domain;
        this.scheme = config.scheme;
        this.protocol = config.protocol;
        this.apiURL = config.apiURL;
        this.sandboxBaseURL = config.sandboxBaseURL;
        this.debug = config.debug;
        this.requestTimeoutMs = config.requestTimeoutMs;
        this.port = config.port;
        this.headers = new HashMap<>(config.headers);
    }

    public static ConnectionConfig create() {
        return new Builder().build();
    }

    /** API base URL, automatically selects NATIVE/PRIVATE format based on protocol. */
    public String getAPIURL() {
        if (apiURL != null && !apiURL.isEmpty()) {
            return apiURL;
        }
        String s = getSchemeOrDefault();
        if (protocol == Protocol.PRIVATE) {
            return String.format("%s://%s/kruise/api", s, domain);
        }
        return String.format("%s://api.%s", s, domain);
    }

    /** Envd URL for a specific sandbox, automatically selects NATIVE/PRIVATE format based on protocol. */
    public String getSandboxURL(String sandboxID) {
        if (sandboxBaseURL != null && !sandboxBaseURL.isEmpty()) {
            return String.format("%s/%s", sandboxBaseURL, sandboxID);
        }
        String s = getSchemeOrDefault();
        if (protocol == Protocol.PRIVATE) {
            return String.format("%s://%s/kruise/%s/%d", s, domain, sandboxID, port);
        }
        return String.format("%s://%d-%s.%s", s, port, sandboxID, domain);
    }

    /** Shared ApiClient with double-checked locking lazy initialization. */
    public ApiClient getOrCreateApiClient() {
        if (apiClient == null) {
            synchronized (lock) {
                if (apiClient == null) {
                    ApiClient client = new ApiClient();
                    client.setBasePath(getAPIURL());
                    client.setConnectTimeout((int)requestTimeoutMs);
                    client.setReadTimeout((int)requestTimeoutMs);
                    if (apiKey != null && !apiKey.isEmpty()) {
                        client.addDefaultHeader("X-API-Key", apiKey);
                    }
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        client.addDefaultHeader(entry.getKey(), entry.getValue());
                    }
                    this.apiClient = client;
                }
            }
        }
        return apiClient;
    }

    SandboxesApi getOrCreateSandboxesApi() {
        if (sandboxesApi == null) {
            synchronized (lock) {
                if (sandboxesApi == null) {
                    sandboxesApi = new SandboxesApi(getOrCreateApiClient());
                }
            }
        }
        return sandboxesApi;
    }

    private String getSchemeOrDefault() {
        return (scheme != null && !scheme.isEmpty()) ? scheme : DEFAULT_SCHEME;
    }

    public String getApiKey() {return apiKey;}

    public String getAccessToken() {return accessToken;}

    public String getDomain() {return domain;}

    public String getScheme() {return scheme;}

    public Protocol getProtocol() {return protocol;}

    public String getApiURL() {return apiURL;}

    public String getSandboxBaseURL() {return sandboxBaseURL;}

    public boolean isDebug() {return debug;}

    public long getRequestTimeoutMs() {return requestTimeoutMs;}

    public int getPort() {return port;}

    public Map<String, String> getHeaders() {return headers;}

    public static class Builder {
        private final ConnectionConfig config = new ConnectionConfig();

        public Builder() {
            // Environment variables as defaults
            String envApiKey = System.getenv("X_API_KEY");
            if (envApiKey != null && !envApiKey.isEmpty()) {
                config.apiKey = envApiKey;
            }
            String envScheme = System.getenv("SCHEME");
            if (envScheme != null && !envScheme.isEmpty()) {
                config.scheme = envScheme;
            }
            String envProtocol = System.getenv("PROTOCOL");
            if (envProtocol != null && !envProtocol.isEmpty()) {
                config.protocol = Protocol.valueOf(envProtocol.toUpperCase());
            }
        }

        public Builder apiKey(String apiKey) {
            config.apiKey = apiKey;
            return this;
        }

        public Builder accessToken(String accessToken) {
            config.accessToken = accessToken;
            return this;
        }

        public Builder domain(String domain) {
            config.domain = domain;
            return this;
        }

        public Builder scheme(String scheme) {
            config.scheme = scheme;
            return this;
        }

        public Builder protocol(Protocol protocol) {
            config.protocol = protocol;
            return this;
        }

        public Builder apiURL(String apiURL) {
            config.apiURL = apiURL;
            return this;
        }

        public Builder sandboxBaseURL(String sandboxBaseURL) {
            config.sandboxBaseURL = sandboxBaseURL;
            return this;
        }

        public Builder debug(boolean debug) {
            config.debug = debug;
            return this;
        }

        public Builder requestTimeoutMs(long timeoutMs) {
            config.requestTimeoutMs = timeoutMs;
            return this;
        }

        public Builder port(int port) {
            config.port = port;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            config.headers.putAll(headers);
            return this;
        }

        public Builder addHeader(String key, String value) {
            config.headers.put(key, value);
            return this;
        }

        public ConnectionConfig build() {
            return new ConnectionConfig(config);
        }
    }
}
