package io.openkruise.agents.client.e2b;

import io.openkruise.agents.client.e2b.api.invoker.ApiException;
import io.openkruise.agents.client.runtime.RuntimeClient;
import io.openkruise.agents.client.runtime.commands.Commands;
import io.openkruise.agents.client.runtime.filesystem.Filesystem;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a created/connected E2B sandbox instance (data plane) with try-with-resources support for automatic termination.
 */
public class Sandbox implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Sandbox.class.getName());

    /** Command execution within the sandbox */
    public final Commands commands;
    /** File operations within the sandbox */
    public final Filesystem files;

    private final String sandboxID;
    private final ConnectionConfig config;
    private final SandboxApi sandboxApi;
    private final RuntimeClient runtimeClient;

    Sandbox(String sandboxID, String envdAccessToken, ConnectionConfig config, SandboxApi sandboxApi) {
        this.sandboxID = sandboxID;
        this.config = config;
        this.sandboxApi = sandboxApi;

        E2bRuntimeConfig runtimeConfig = E2bRuntimeConfig.fromConnectionConfig(config, envdAccessToken);
        this.runtimeClient = RuntimeClient.create(sandboxID, runtimeConfig);
        this.commands = runtimeClient.commands;
        this.files = runtimeClient.files;
    }

    public String getSandboxID() {
        return sandboxID;
    }

    public ConnectionConfig getConfig() {
        return config;
    }

    public String getSandboxURL() {
        return config.getSandboxURL(sandboxID);
    }

    public RuntimeClient getRuntimeClient() {
        return runtimeClient;
    }

    /** Closes and terminates the sandbox. Termination failures are logged but not thrown. */
    @Override
    public void close() {
        if (runtimeClient != null) {
            runtimeClient.close();
        }
        try {
            sandboxApi.kill(sandboxID);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to kill sandbox " + sandboxID + " on close()", e);
        }
    }

    @Override
    public String toString() {
        return "Sandbox{sandboxID='" + sandboxID + "'}";
    }
}
