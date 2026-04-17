package io.openkruise.agents.client.e2b.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.openkruise.agents.client.e2b.api.invoker.ApiException;
import io.openkruise.agents.client.e2b.api.models.NewSandbox;
import io.openkruise.agents.client.e2b.api.models.Sandbox;
import io.openkruise.agents.client.e2b.config.ConnectionConfig;
import io.openkruise.agents.client.e2b.sandbox.SandboxClient;
import io.openkruise.agents.client.e2b.sandbox.commands.CommandHandle;
import io.openkruise.agents.client.e2b.sandbox.commands.CommandResult;
import io.openkruise.agents.client.e2b.sandbox.commands.Commands;
import io.openkruise.agents.client.e2b.sandbox.commands.Commands.ProcessInfo;
import io.openkruise.agents.client.e2b.sandbox.filesystem.Filesystem;
import io.openkruise.agents.client.e2b.sandbox.filesystem.Filesystem.EntryInfo;

public class SandboxClientExample {
    private static final String API_KEY = "key";
    private static final String SANDBOX_DOMAIN = "your.domain.com";

    public static void main(String[] args) throws ApiException {
        String sandboxId = "";
        // Configure connection parameters
        ConnectionConfig config = new ConnectionConfig.Builder()
            .apiKey(API_KEY)
            .domain(SANDBOX_DOMAIN)
            .build();

        SandboxClient client = new SandboxClient(config);
        try {
            NewSandbox newSandbox = new NewSandbox();
            newSandbox.setTemplateID("code-interpreter");
            newSandbox.setTimeout(3600);
            Sandbox sandbox = client.createSandbox(newSandbox);
            sandboxId = sandbox.getSandboxID();
            System.out.println("Successfully connected to sandbox: " + sandboxId);

            System.out.println("\n--- Process Operations Demo ---");
            demonstrateCommandOperations(client.commands());

            // Perform comprehensive filesystem operations
            System.out.println("\n--- Filesystem Operations Demo ---");
            demonstrateFileOperations(client.filesystem());

            client.deleteSandbox(sandboxId);
            System.out.println("Successfully delete to sandbox: " + sandboxId);

            // Close client after completing operations
            client.close();
            System.out.println("\nSandbox client closed");

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
            if (sandboxId != "") {
                client.deleteSandbox(sandboxId);
                client.close();
            }
        }
    }

    private static void demonstrateCommandOperations(Commands commands) {
        try {
            System.out.println("\n1. Testing run() method:");
            // Run a simple command to display current working directory
            CommandResult result = commands.run("pwd");
            System.out.println(
                "Run 'pwd' command result - Exit code: " + result.getExitCode() + ", Output: " + result.getStdout());

            // Run a command with options - using compatible map creation
            Map<String, String> envs = new HashMap<>();
            envs.put("TEST_VAR", "test_value");
            Commands.RunOptions options = new Commands.RunOptions()
                .cwd("/tmp")
                .envs(envs);
            CommandResult resultWithOptions = commands.run("echo $TEST_VAR && pwd", options);
            System.out.println("Run command with options result - Exit code: " + resultWithOptions.getExitCode() +
                ", Output: " + resultWithOptions.getStdout());

            System.out.println("\n2. Testing list() method:");
            // List all processes in the sandbox
            List<ProcessInfo> processList = commands.list();
            System.out.println("Current processes count: " + processList.size());
            for (ProcessInfo process : processList) {
                System.out.println("  - PID: " + process.getPid() + ", Command: " + process.getCmd() +
                    ", Working Directory: " + process.getCwd());
            }

            System.out.println("\n3. Testing runBackground() method (to demonstrate other operations):");
            // Start a long-running command in the background for testing other operations
            CommandHandle handle = commands.runBackground("sleep 60", new Commands.RunOptions());
            long pid = handle.getPid();
            System.out.println("Started background process with PID: " + pid);

            // Wait a moment for the process to start
            Thread.sleep(1000);

            System.out.println("\n4. Testing list() method again to see the new process:");
            processList = commands.list();
            System.out.println("Updated processes count: " + processList.size());
            boolean foundNewProcess = false;
            for (ProcessInfo process : processList) {
                System.out.println("  - PID: " + process.getPid() + ", Command: " + process.getCmd() +
                    ", Working Directory: " + process.getCwd());
                if (process.getPid() == pid) {
                    foundNewProcess = true;
                    System.out.println("    ^ Found our background process");
                }
            }
            if (!foundNewProcess) {
                System.out.println("    - Could not find our background process in the list");
            }

            System.out.println("\n5. Testing sendInput() method:");
            // Test sendInput - though this would typically be used with interactive processes
            try {
                commands.sendInput((int)pid, "sample input\n");
                System.out.println("Sent input to process PID: " + pid);
            } catch (Exception e) {
                System.out.println(
                    "Sending input failed (this is expected for non-interactive processes): " + e.getMessage());
            }

            System.out.println("\n6. Testing connect() method:");
            // Connect to the running process
            try {
                CommandHandle connectedHandle = commands.connect((int)pid);
                System.out.println("Connected to process PID: " + connectedHandle.getPid());

                // Wait briefly and then disconnect
                Thread.sleep(1000);
                connectedHandle.close();
                System.out.println("Disconnected from process");
            } catch (Exception e) {
                System.out.println("Connecting to process failed (expected for sleep command): " + e.getMessage());
            }

            System.out.println("\n7. Testing closeStdin() method:");
            // Close stdin of the process
            try {
                commands.closeStdin((int)pid);
                System.out.println("Closed stdin for process PID: " + pid);
            } catch (Exception e) {
                System.out.println("Closing stdin failed: " + e.getMessage());
            }

            System.out.println("\n8. Testing kill() method:");
            // Kill the background process
            boolean killed = commands.kill((int)pid);
            System.out.println("Attempted to kill process PID: " + pid + ", Success: " + killed);

            // Verify the process was killed by listing processes again
            Thread.sleep(1000);
            processList = commands.list();
            boolean processStillRunning = false;
            for (ProcessInfo process : processList) {
                if (process.getPid() == pid) {
                    processStillRunning = true;
                    break;
                }
            }
            if (!processStillRunning) {
                System.out.println("Verified: Process " + pid + " is no longer running");
            } else {
                System.out.println("Warning: Process " + pid + " still appears to be running");
            }

            System.out.println("Command operations demonstration completed.");

        } catch (Exception e) {
            System.err.println("Error during command operations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void demonstrateFileOperations(Filesystem filesystem) {
        try {
            // 1. Create a test directory
            String testDir = "/tmp/test_" + System.currentTimeMillis();
            System.out.println("\n1. Creating directory: " + testDir);
            boolean created = filesystem.makeDir(testDir);
            System.out.println("Directory created: " + created);

            // 2. Check if directory exists
            System.out.println("\n2. Checking if directory exists: " + testDir);
            boolean exists = filesystem.exists(testDir);
            System.out.println("Directory exists: " + exists);

            // 3. Get directory information
            System.out.println("\n3. Getting directory information for: " + testDir);
            EntryInfo dirInfo = filesystem.getInfo(testDir);
            System.out.println("Directory info: " + dirInfo);

            // 4. Create a subdirectory
            String subDir = testDir + "/subdir";
            System.out.println("\n4. Creating subdirectory: " + subDir);
            boolean subCreated = filesystem.makeDir(subDir);
            System.out.println("Subdirectory created: " + subCreated);

            // 5. List the test directory contents
            System.out.println("\n5. Listing contents of: " + testDir);
            List<EntryInfo> dirContents = filesystem.listDir(testDir, 1);
            System.out.println("Items in directory: " + dirContents.size());
            for (EntryInfo item : dirContents) {
                System.out.println("  - " + item.getType() + " " + item.getName() + " (size: " + item.getSize() + ")");
            }

            // 6. Move/rename the subdirectory
            String newSubDir = testDir + "/renamed_subdir";
            System.out.println("\n6. Moving " + subDir + " to " + newSubDir);
            boolean moved = filesystem.move(subDir, newSubDir);
            System.out.println("Move operation successful: " + moved);

            // 7. List the test directory contents again to see the rename
            System.out.println("\n7. Listing contents of: " + testDir + " after move operation");
            dirContents = filesystem.listDir(testDir, 1);
            System.out.println("Items in directory: " + dirContents.size());
            for (EntryInfo item : dirContents) {
                System.out.println("  - " + item.getType() + " " + item.getName() + " (size: " + item.getSize() + ")");
            }

            // 8. Create another test directory to demonstrate exists functionality
            String anotherDir = testDir + "/another_test_dir";
            System.out.println("\n8. Creating another directory: " + anotherDir);
            filesystem.makeDir(anotherDir);
            System.out.println("Another directory created: " + anotherDir);

            // 9. Check if the new directory exists
            System.out.println("\n9. Checking if " + anotherDir + " exists");
            boolean anotherExists = filesystem.exists(anotherDir);
            System.out.println("Directory exists: " + anotherExists);

            // 10. Get info for the new directory
            System.out.println("\n10. Getting information for: " + anotherDir);
            EntryInfo anotherInfo = filesystem.getInfo(anotherDir);
            System.out.println("Another directory info: " + anotherInfo);

            // 11. Remove the test directories
            System.out.println("\n11. Removing test directory and its contents: " + testDir);
            filesystem.remove(testDir);
            System.out.println("Directory removed: " + testDir);

            // 12. Verify removal
            System.out.println("\n12. Verifying directory removal");
            boolean stillExists = filesystem.exists(testDir);
            System.out.println("Directory still exists after removal: " + stillExists);

        } catch (Exception e) {
            System.err.println("Error during filesystem operations: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
