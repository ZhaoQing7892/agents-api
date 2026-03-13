package io.openkruise.agents.client.e2b.sandbox.filesystem;

import io.grpc.stub.StreamObserver;
import io.openkruise.agents.client.e2b.envd.filesystem.EventType;
import io.openkruise.agents.client.e2b.envd.filesystem.WatchDirResponse;

import java.util.function.Consumer;

public class WatchHandle {
    private final StreamObserver<WatchDirResponse> responseObserver;
    private volatile boolean stopped = false;

    public WatchHandle(StreamObserver<WatchDirResponse> responseObserver) {
        this.responseObserver = responseObserver;
    }

    /**
     * Stop watching the directory.
     */
    public void stop() {
        if (!stopped) {
            try {
                // 使用onCompleted而不是onCompleted来结束观察者
                responseObserver.onCompleted();
            } catch (Exception e) {
                System.err.println("Error completing the response observer: " + e.getMessage());
            } finally {
                stopped = true;
            }
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public enum FilesystemEventType {
        CHMOD,
        CREATE,
        REMOVE,
        RENAME,
        WRITE
    }

    public static class FilesystemEvent {
        private final String name;
        private final FilesystemEventType type;

        public FilesystemEvent(String name, FilesystemEventType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public FilesystemEventType getType() {
            return type;
        }

        @Override
        public String toString() {
            return String.format("FilesystemEvent{name='%s', type=%s}", name, type);
        }
    }
}
