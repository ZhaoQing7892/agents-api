package io.openkruise.agents.client.examples.runtime;

import io.openkruise.agents.client.runtime.RuntimeConfig;
import io.openkruise.agents.client.runtime.RuntimeClient;
import io.openkruise.agents.client.runtime.commands.CommandResult;
import io.openkruise.agents.client.runtime.filesystem.Filesystem.EntryInfo;

import java.util.List;

public class K8sDirectConnectExample {

    public static void main(String[] args) {
        // Sandbox gateway address
        // In cluster: "sandbox-gateway.sandbox-system.svc:7788"
        // Local development: "127.0.0.1:7788"
        String domain = "sandbox-gateway.sandbox-system.svc:7788";
        String namespace = "default";
        String sandboxName = "your-sandbox-name";

        // Build RuntimeConfig
        RuntimeConfig config = new RuntimeConfig.Builder()
            .domain(domain)
            .scheme("http")
            .build();

        try (RuntimeClient client = RuntimeClient.newFromK8s(namespace, sandboxName, config)) {
            System.out.println("Runtime URL: " + client.getRuntimeURL());
            System.out.println("Sandbox ID: " + client.getSandboxID());

            // Execute command
            CommandResult result = client.Commands.run("uname -a");
            System.out.println("uname: " + result.getStdout());

            // List directory
            List<EntryInfo> entries = client.Files.listDir("/");
            System.out.println("Root directory file count: " + entries.size());
            for (EntryInfo entry : entries) {
                System.out.println("  " + entry);
            }

            // Create directory
            client.Files.makeDir("/tmp/test-dir");

            // Check if file exists
            boolean exists = client.Files.exists("/tmp/test-dir");
            System.out.println("/tmp/test-dir exists: " + exists);

            // Execute multiple commands
            CommandResult whoami = client.Commands.run("whoami");
            System.out.println("Current user: " + whoami.getStdout().trim());

            CommandResult pwd = client.Commands.run("pwd");
            System.out.println("Working directory: " + pwd.getStdout().trim());

        } catch (Exception e) {
            System.err.println("K8s direct connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
