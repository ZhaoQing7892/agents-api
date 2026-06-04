package io.openkruise.agents.client.e2b.sandbox.commands;

import io.grpc.StatusRuntimeException;
import io.openkruise.agents.client.e2b.envd.process.ProcessGrpc;
import io.openkruise.agents.client.e2b.envd.process.StartResponse;
import io.openkruise.agents.client.e2b.envd.process.ProcessEvent;
import io.openkruise.agents.client.e2b.envd.process.ProcessInput;
import io.openkruise.agents.client.e2b.envd.process.ProcessSelector;
import io.openkruise.agents.client.e2b.envd.process.SendInputRequest;
import io.openkruise.agents.client.e2b.envd.process.SendSignalRequest;
import io.openkruise.agents.client.e2b.envd.process.Signal;
import io.openkruise.agents.client.e2b.config.ConnectionConfig;
import io.openkruise.agents.client.e2b.exceptions.SandboxException;
import io.openkruise.agents.client.e2b.utils.RpcUtils;

import com.google.protobuf.ByteString;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CommandHandle implements AutoCloseable {
    private final long pid;
    private final Iterator<StartResponse> events;
    private final ProcessGrpc.ProcessBlockingStub processStub;
    private final ConnectionConfig config;
    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();
    private final AtomicInteger exitCode = new AtomicInteger(-1);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final Consumer<String> onStdout;
    private final Consumer<String> onStderr;

    public CommandHandle(long pid, Iterator<StartResponse> events,
        ProcessGrpc.ProcessBlockingStub processStub,
        ConnectionConfig config,
        Consumer<String> onStdout, Consumer<String> onStderr) {
        if (pid <= 0) {
            throw new IllegalArgumentException("PID must be positive, got: " + pid);
        }
        this.pid = pid;
        this.events = events;
        this.processStub = processStub;
        this.config = config;
        this.onStdout = onStdout;
        this.onStderr = onStderr;
    }

    public long getPid() {
        return pid;
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public CommandResult waitForCompletion() {
        if (completed.get()) {
            return createResult();
        }

        try {
            while (events != null && events.hasNext()) {
                StartResponse response = events.next();
                processResponse(response);

                if (completed.get()) {
                    break;
                }
            }
        } catch (StatusRuntimeException e) {
            throw RpcUtils.handleRpcException(e);
        } catch (Exception e) {
            throw new SandboxException("Error while waiting for command completion", e);
        } finally {
            synchronized (this) {
                completed.set(true);
                close();
            }
        }

        return createResult();
    }

    private CommandResult createResult() {
        return new CommandResult(stdout.toString(), stderr.toString(), exitCode.get());
    }

    private void processResponse(StartResponse response) {
        if (!response.hasEvent()) {
            return;
        }

        ProcessEvent event = response.getEvent();

        if (event.hasData()) {
            ProcessEvent.DataEvent dataEvent = event.getData();

            if (dataEvent.hasStdout()) {
                String out = dataEvent.getStdout().toStringUtf8();
                synchronized (stdout) {
                    stdout.append(out);
                }
                if (onStdout != null) {
                    onStdout.accept(out);
                }
            }

            if (dataEvent.hasStderr()) {
                String err = dataEvent.getStderr().toStringUtf8();
                synchronized (stderr) {
                    stderr.append(err);
                }
                if (onStderr != null) {
                    onStderr.accept(err);
                }
            }
        }

        if (event.hasEnd()) {
            ProcessEvent.EndEvent endEvent = event.getEnd();
            exitCode.set(endEvent.getExitCode());
            completed.set(true);
        }
    }

    public void sendStdin(String data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (completed.get()) {
            throw new IllegalStateException("Process has already completed");
        }

        SendInputRequest request = SendInputRequest.newBuilder()
            .setProcess(ProcessSelector.newBuilder().setPid((int)pid).build())
            .setInput(ProcessInput.newBuilder()
                .setStdin(ByteString.copyFromUtf8(data))
                .build())
            .build();

        try {
            processStub.withDeadlineAfter(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS)
                .sendInput(request);
        } catch (StatusRuntimeException e) {
            throw RpcUtils.handleRpcException(e);
        }
    }

    public boolean kill() {
        return kill(Signal.SIGNAL_SIGKILL);
    }

    public boolean kill(Signal signal) {
        if (completed.get()) {
            return false;
        }

        SendSignalRequest request = SendSignalRequest.newBuilder()
            .setProcess(ProcessSelector.newBuilder().setPid((int)pid).build())
            .setSignal(signal)
            .build();

        try {
            processStub.withDeadlineAfter(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS)
                .sendSignal(request);
            exitCode.set(-1);
            completed.set(true);
            return true;
        } catch (StatusRuntimeException e) {
            if (RpcUtils.isNotFoundError(e)) {
                return false;
            }
            throw RpcUtils.handleRpcException(e);
        }
    }

    @Override
    public void close() {
        closed.compareAndSet(false, true);
    }
}
