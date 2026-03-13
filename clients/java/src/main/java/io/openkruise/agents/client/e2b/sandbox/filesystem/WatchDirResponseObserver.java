package io.openkruise.agents.client.e2b.sandbox.filesystem;

import io.grpc.stub.StreamObserver;
import io.openkruise.agents.client.e2b.envd.filesystem.EventType;
import io.openkruise.agents.client.e2b.envd.filesystem.FilesystemEvent;
import io.openkruise.agents.client.e2b.envd.filesystem.WatchDirResponse;

import java.util.function.Consumer;

public class WatchDirResponseObserver implements StreamObserver<WatchDirResponse> {
    private final Consumer<WatchHandle.FilesystemEvent> onEvent;

    public WatchDirResponseObserver(Consumer<WatchHandle.FilesystemEvent> onEvent) {
        this.onEvent = onEvent;
    }

    @Override
    public void onNext(WatchDirResponse response) {
        // Process the response
        if (response.getEventCase() == WatchDirResponse.EventCase.FILESYSTEM) {
            FilesystemEvent fsEvent = response.getFilesystem();

            WatchHandle.FilesystemEventType eventType = mapEventType(fsEvent.getType());
            if (eventType != null) {
                WatchHandle.FilesystemEvent event = new WatchHandle.FilesystemEvent(
                    fsEvent.getName(),
                    eventType
                );

                onEvent.accept(event);
            }
        }
    }

    @Override
    public void onError(Throwable t) {
    }

    @Override
    public void onCompleted() {
    }

    private WatchHandle.FilesystemEventType mapEventType(EventType eventType) {
        switch (eventType) {
            case EVENT_TYPE_CHMOD:
                return WatchHandle.FilesystemEventType.CHMOD;
            case EVENT_TYPE_CREATE:
                return WatchHandle.FilesystemEventType.CREATE;
            case EVENT_TYPE_REMOVE:
                return WatchHandle.FilesystemEventType.REMOVE;
            case EVENT_TYPE_RENAME:
                return WatchHandle.FilesystemEventType.RENAME;
            case EVENT_TYPE_WRITE:
                return WatchHandle.FilesystemEventType.WRITE;
            default:
                return null;
        }
    }
}
