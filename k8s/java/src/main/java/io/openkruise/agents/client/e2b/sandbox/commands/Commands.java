package io.openkruise.agents.client.e2b.sandbox.commands;

import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.openkruise.agents.client.e2b.api.models.Sandbox;
import io.openkruise.agents.client.e2b.config.ConnectionConfig;
import io.openkruise.agents.client.e2b.envd.process.*;
import io.openkruise.agents.client.e2b.exceptions.SandboxException;
import io.openkruise.agents.client.e2b.utils.RpcUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Commands {
    public static final String SERVICE_NAME = "process.Process";
    private static final JsonFormat.Parser PROTO_PARSER = JsonFormat.parser().ignoringUnknownFields();
    private final Sandbox sandbox;
    private final ProcessGrpc.ProcessBlockingStub blockingStub;
    private final ConnectionConfig config;
    private final OkHttpClient httpClient;

    public Commands(Sandbox sandbox, ManagedChannel channel, ConnectionConfig config) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox cannot be null");
        this.blockingStub = ProcessGrpc.newBlockingStub(channel);
        this.config = Objects.requireNonNull(config, "ConnectionConfig cannot be null");
        this.httpClient = new OkHttpClient.Builder().connectTimeout(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS).build();
    }

    public List<ProcessInfo> list() {
        try {
            ListRequest params = ListRequest.newBuilder().build();

            Request request = config.buildHttpRequest(SERVICE_NAME, "List", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("List HTTP request failed: " + response.code() + " " + response.message());
            }

            Reader reader = response.body().charStream();
            // Parse the JSON response back to protobuf
            ListResponse.Builder builder = ListResponse.newBuilder();
            PROTO_PARSER.merge(reader, builder);
            ListResponse listResponse = builder.build();

            List<ProcessInfo> entries = new ArrayList<>(listResponse.getProcessesCount());
            for (io.openkruise.agents.client.e2b.envd.process.ProcessInfo entry :
                listResponse.getProcessesList()) {
                entries.add(toProcessInfo(entry));
            }
            return entries;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list", e);
        }
    }

    private ProcessInfo toProcessInfo(io.openkruise.agents.client.e2b.envd.process.ProcessInfo p) {
        return new ProcessInfo(
            p.getPid(),
            p.hasTag() ? p.getTag() : null,
            p.getConfig().getCmd(),
            p.getConfig().getArgsList(),
            p.getConfig().getEnvsMap(),
            p.getConfig().hasCwd() ? p.getConfig().getCwd() : null
        );
    }

    public CommandResult run(String cmd) {
        return run(cmd, new RunOptions());
    }

    public CommandResult run(String cmd, RunOptions options) {
        CommandHandle handle = null;
        try {
            handle = runBackground(cmd, options);
            return handle.waitForCompletion();
        } finally {
            if (handle != null) {
                handle.close();
            }
        }
    }

    public CommandHandle runBackground(String cmd, RunOptions options) {
        if (cmd == null || cmd.trim().isEmpty()) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
        if (options == null) {
            options = new RunOptions();
        }

        ProcessConfig.Builder processConfig = ProcessConfig.newBuilder()
            .setCmd("/bin/bash")
            .addArgs("-l")
            .addArgs("-c")
            .addArgs(cmd);

        if (options.getCwd() != null) {
            processConfig.setCwd(options.getCwd());
        }

        if (options.getEnvs() != null) {
            processConfig.putAllEnvs(options.getEnvs());
        }

        StartRequest request = StartRequest.newBuilder()
            .setProcess(processConfig)
            .setStdin(options.isStdin())
            .build();

        Iterator<StartResponse> events;
        try {
            events = blockingStub
                .withDeadlineAfter(
                    options.getTimeoutMs() != null ? options.getTimeoutMs() : config.getRequestTimeoutMs(),
                    TimeUnit.MILLISECONDS)
                .start(request);
        } catch (StatusRuntimeException e) {
            throw RpcUtils.handleRpcException(e);
        }

        if (!events.hasNext()) {
            throw new SandboxException("Failed to start process: no response from server");
        }

        StartResponse firstEvent = events.next();
        if (!firstEvent.hasEvent() || !firstEvent.getEvent().hasStart()) {
            throw new SandboxException("Failed to start process: invalid response from server");
        }

        long pid = firstEvent.getEvent().getStart().getPid();
        if (pid <= 0) {
            throw new SandboxException("Failed to start process: invalid PID received");
        }

        return new CommandHandle(pid, events, blockingStub, config,
            options.getOnStdout(), options.getOnStderr());
    }

    public void sendInput(int pid, String data) {
        validatePid(pid);
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        SendInputRequest params = SendInputRequest.newBuilder()
            .setProcess(ProcessSelector.newBuilder().setPid(pid).build())
            .setInput(ProcessInput.newBuilder()
                .setStdin(com.google.protobuf.ByteString.copyFromUtf8(data))
                .build())
            .build();

        try {
            Request request = config.buildHttpRequest(SERVICE_NAME, "SendInput", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("SendInput HTTP request failed: " + response.code() + " " + response.message());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send input", e);
        }
    }

    public boolean kill(int pid) {
        return sendSignal(pid, Signal.SIGNAL_SIGKILL);
    }

    public boolean sendSignal(int pid, Signal signal) {
        validatePid(pid);

        SendSignalRequest params = SendSignalRequest.newBuilder()
            .setProcess(ProcessSelector.newBuilder().setPid((int)pid).build())
            .setSignal(signal != null ? signal : Signal.SIGNAL_UNSPECIFIED)
            .build();

        try {
            Request request = config.buildHttpRequest(SERVICE_NAME, "SendSignal", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                return false;
            }
            if (!response.isSuccessful()) {
                throw new IOException("SendInput HTTP request failed: " + response.code() + " " + response.message());
            }
            return response.isSuccessful();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send kill", e);
        }
    }

    public void closeStdin(int pid) {
        validatePid(pid);
        CloseStdinRequest params = CloseStdinRequest.newBuilder()
            .setProcess(ProcessSelector.newBuilder().setPid((int)pid).build())
            .build();

        try {
            Request request = config.buildHttpRequest(SERVICE_NAME, "CloseStdin", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("CloseStdin HTTP request failed: " + response.code() + " " + response.message());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to closeStdin", e);
        }
    }

    public CommandHandle connect(int pid) {
        validatePid(pid);

        ConnectRequest request = ConnectRequest.newBuilder()
            .setProcess(ProcessSelector.newBuilder().setPid((int)pid).build())
            .build();

        Iterator<ConnectResponse> events;
        try {
            events = blockingStub
                .withDeadlineAfter(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS)
                .connect(request);
        } catch (StatusRuntimeException e) {
            throw RpcUtils.handleRpcException(e);
        }

        if (!events.hasNext()) {
            throw new SandboxException("Failed to connect to process: no response from server");
        }

        ConnectResponse firstResponse = events.next();
        if (!firstResponse.hasEvent()) {
            throw new SandboxException("Failed to connect to process: invalid response from server");
        }

        ProcessEvent event = firstResponse.getEvent();
        if (!event.hasStart()) {
            throw new SandboxException("Failed to connect to process: expected start event");
        }

        long connectedPid = event.getStart().getPid();
        if (connectedPid != pid) {
            throw new SandboxException(
                "Failed to connect to process: PID mismatch, expected " + pid + " but got " + connectedPid);
        }

        return new CommandHandle(pid, new ConnectResponseIterator(events), blockingStub, config, null, null);
    }

    private void validatePid(int pid) {
        if (pid <= 0) {
            throw new IllegalArgumentException("PID must be positive");
        }
    }

    private static class ConnectResponseIterator implements Iterator<StartResponse> {
        private final Iterator<ConnectResponse> delegate;

        ConnectResponseIterator(Iterator<ConnectResponse> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public StartResponse next() {
            ConnectResponse response = delegate.next();
            if (!response.hasEvent()) {
                return StartResponse.getDefaultInstance();
            }

            ProcessEvent event = response.getEvent();
            return StartResponse.newBuilder()
                .setEvent(event)
                .build();
        }
    }

    public static class RunOptions {
        private String cwd;
        private Map<String, String> envs;
        private boolean stdin = false;
        private Long timeoutMs;
        private Consumer<String> onStdout;
        private Consumer<String> onStderr;

        public RunOptions cwd(String cwd) {
            this.cwd = cwd;
            return this;
        }

        public RunOptions envs(Map<String, String> envs) {
            if (envs != null) {
                this.envs = new HashMap<String, String>(envs);
            }
            return this;
        }

        public RunOptions stdin(boolean stdin) {
            this.stdin = stdin;
            return this;
        }

        public RunOptions timeoutMs(long timeoutMs) {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.timeoutMs = timeoutMs;
            return this;
        }

        public RunOptions onStdout(Consumer<String> onStdout) {
            this.onStdout = onStdout;
            return this;
        }

        public RunOptions onStderr(Consumer<String> onStderr) {
            this.onStderr = onStderr;
            return this;
        }

        public String getCwd() {
            return cwd;
        }

        public Map<String, String> getEnvs() {
            if (envs == null) {
                return null;
            }
            return Collections.unmodifiableMap(new HashMap<String, String>(envs));
        }

        public boolean isStdin() {
            return stdin;
        }

        public Long getTimeoutMs() {
            return timeoutMs;
        }

        public Consumer<String> getOnStdout() {
            return onStdout;
        }

        public Consumer<String> getOnStderr() {
            return onStderr;
        }
    }

    public static class ProcessInfo {
        private final long pid;
        private final String tag;
        private final String cmd;
        private final List<String> args;
        private final Map<String, String> envs;
        private final String cwd;

        public ProcessInfo(long pid, String tag, String cmd, List<String> args,
            Map<String, String> envs, String cwd) {
            if (pid <= 0) {
                throw new IllegalArgumentException("PID must be positive");
            }
            this.pid = pid;
            this.tag = tag;
            this.cmd = Objects.requireNonNull(cmd, "Command cannot be null");
            if (args != null) {
                this.args = Collections.unmodifiableList(new ArrayList<String>(args));
            } else {
                this.args = Collections.emptyList();
            }
            if (envs != null) {
                this.envs = Collections.unmodifiableMap(new HashMap<String, String>(envs));
            } else {
                this.envs = Collections.emptyMap();
            }
            this.cwd = cwd;
        }

        public long getPid() {
            return pid;
        }

        public String getTag() {
            return tag;
        }

        public String getCmd() {
            return cmd;
        }

        public List<String> getArgs() {
            return args;
        }

        public Map<String, String> getEnvs() {
            return envs;
        }

        public String getCwd() {
            return cwd;
        }

        @Override
        public String toString() {
            return String.format("ProcessInfo{pid=%d, cmd='%s', cwd='%s'}", pid, cmd, cwd);
        }
    }
}
