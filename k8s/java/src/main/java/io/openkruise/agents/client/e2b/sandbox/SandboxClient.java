package io.openkruise.agents.client.e2b.sandbox;

import io.grpc.ManagedChannel;
import io.openkruise.agents.client.e2b.api.SandboxesApi;
import io.openkruise.agents.client.e2b.api.invoker.ApiClient;
import io.openkruise.agents.client.e2b.api.invoker.ApiException;
import io.openkruise.agents.client.e2b.api.invoker.Configuration;
import io.openkruise.agents.client.e2b.api.models.NewSandbox;
import io.openkruise.agents.client.e2b.api.models.Sandbox;
import io.openkruise.agents.client.e2b.api.models.SandboxState;
import io.openkruise.agents.client.e2b.api.models.SandboxesGet200ResponseInner;
import io.openkruise.agents.client.e2b.api.models.SandboxDetail;
import io.openkruise.agents.client.e2b.api.models.SandboxMetric;
import io.openkruise.agents.client.e2b.api.models.ConnectSandbox;
import io.openkruise.agents.client.e2b.config.ConnectionConfig;
import io.openkruise.agents.client.e2b.sandbox.commands.Commands;
import io.openkruise.agents.client.e2b.sandbox.filesystem.Filesystem;

import java.util.List;

public class SandboxClient {
    private ConnectionConfig config;
    private SandboxesApi sandboxesApi;
    private Sandbox sandbox;
    private ManagedChannel channel;
    private Commands commands;
    private Filesystem filesystem;

    public SandboxClient(ConnectionConfig config) {
        this.config = config;

        // Initialize ApiClient
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(config.getApiUrl());
        apiClient.addDefaultHeader("X-API-Key", config.getApiKey());
        this.sandboxesApi = new SandboxesApi(apiClient);
    }

    /**
     * Create a new sandbox
     *
     * @param newSandbox Sandbox configuration
     * @return Created sandbox object
     * @throws ApiException API call exception
     */
    public Sandbox createSandbox(NewSandbox newSandbox) throws ApiException {
        Sandbox sandbox = sandboxesApi.sandboxesPost(newSandbox);
        initializeSandbox(sandbox);
        return sandbox;
    }

    /**
     * Connect to an existing sandbox
     *
     * @param sandboxId      Sandbox ID
     * @param connectSandbox Connection configuration
     * @return Connected sandbox object
     * @throws ApiException API call exception
     */
    public Sandbox connectSandbox(String sandboxId, ConnectSandbox connectSandbox) throws ApiException {
        Sandbox sandbox = sandboxesApi.sandboxesSandboxIDConnectPost(sandboxId, connectSandbox);
        initializeSandbox(sandbox);
        return sandbox;
    }

    /**
     * Connect to an existing sandbox (using default configuration)
     *
     * @param sandboxId Sandbox ID
     * @return Connected sandbox object
     * @throws ApiException API call exception
     */
    public Sandbox connectSandbox(String sandboxId) throws ApiException {
        ConnectSandbox connectSandbox = new ConnectSandbox();
        return connectSandbox(sandboxId, connectSandbox);
    }

    /**
     * Connect to an existing sandbox (using custom timeout)
     *
     * @param sandboxId Sandbox ID
     * @param timeout   Timeout in seconds, must be between 0 and 2592000
     * @return Connected sandbox object
     * @throws ApiException API call exception
     */
    public Sandbox connectSandbox(String sandboxId, Integer timeout) throws ApiException {
        ConnectSandbox connectSandbox = new ConnectSandbox();
        // Validate timeout value is within valid range
        if (timeout != null && (timeout < 0 || timeout > 2592000)) {
            throw new IllegalArgumentException("Timeout must be between 0 and 2592000 seconds (30 days)");
        }
        connectSandbox.setTimeout(timeout != null ? timeout : 3600);
        return connectSandbox(sandboxId, connectSandbox);
    }

    /**
     * Get sandbox details
     *
     * @param sandboxId Sandbox ID
     * @return Sandbox details
     * @throws ApiException API call exception
     */
    public SandboxDetail getSandboxDetail(String sandboxId) throws ApiException {
        return sandboxesApi.sandboxesSandboxIDGet(sandboxId);
    }

    /**
     * Get all sandboxes list
     *
     * @return Sandbox list
     * @throws ApiException API call exception
     */
    public List<SandboxesGet200ResponseInner> listSandboxes(String metadata, List<SandboxState> state, String nextToken, Integer limit) throws ApiException {
        return sandboxesApi.v2SandboxesGet(metadata, state, nextToken, null);
    }

    /**
     * Delete specified sandbox
     *
     * @param sandboxId Sandbox ID
     * @throws ApiException API call exception
     */
    public void deleteSandbox(String sandboxId) throws ApiException {
        sandboxesApi.sandboxesSandboxIDDelete(sandboxId);
    }

    /**
     * Get sandbox metrics
     *
     * @param sandboxId Sandbox ID
     * @return Sandbox metrics
     * @throws ApiException API call exception
     */
    public List<SandboxMetric> getSandboxMetric(String sandboxId, Long start, Long end) throws ApiException {
        return sandboxesApi.sandboxesSandboxIDMetricsGet(sandboxId, start, end);
    }

    /**
     * Initialize sandbox connection information
     *
     * @param sandbox Sandbox object
     */
    private void initializeSandbox(Sandbox sandbox) {
        this.sandbox = sandbox;
        // Clear previous connection and component instances
        cleanup();
    }

    /**
     * Clean up resources
     */
    private void cleanup() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
        this.channel = null;
        this.commands = null;
        this.filesystem = null;
    }

    /**
     * Get Commands interface for command operations
     * If no connection is established, a gRPC connection to the sandbox will be established first
     *
     * @return Commands interface
     */
    public Commands commands() {
        if (sandbox == null) {
            throw new IllegalStateException("No sandbox connected. Call createSandbox() or connectSandbox() first.");
        }
        if (commands == null) {
            if (channel == null) {
                channel = config.createChannel(sandbox.getSandboxID(), sandbox.getEnvdAccessToken(), sandbox.getDomain());
            }
            commands = new Commands(sandbox, channel, config);
        }
        return commands;
    }

    /**
     * Get Filesystem interface for file operations
     * If no connection is established, a gRPC connection to the sandbox will be established first
     *
     * @return Filesystem interface
     */
    public Filesystem filesystem() {
        if (sandbox == null) {
            throw new IllegalStateException("No sandbox connected. Call createSandbox() or connectSandbox() first.");
        }
        if (filesystem == null) {
            if (channel == null) {
                channel = config.createChannel(sandbox.getSandboxID(), sandbox.getEnvdAccessToken(), sandbox.getDomain());
            }
            filesystem = new Filesystem(sandbox, channel, config);
        }
        return filesystem;
    }

    /**
     * Close the sandbox client and release all resources
     */
    public void close() {
        cleanup();
    }
}
