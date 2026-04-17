package io.openkruise.agents.client.e2b.sandbox.filesystem;

import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.openkruise.agents.client.e2b.api.models.Sandbox;
import io.openkruise.agents.client.e2b.config.ConnectionConfig;
import io.openkruise.agents.client.e2b.envd.filesystem.*;
import io.openkruise.agents.client.e2b.exceptions.SandboxException;
import io.openkruise.agents.client.e2b.utils.RpcUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Filesystem {
    public static final String SERVICE_NAME = "filesystem.Filesystem";
    private static final JsonFormat.Parser PROTO_PARSER = JsonFormat.parser().ignoringUnknownFields();
    private final Sandbox sandbox;
    private final FilesystemGrpc.FilesystemBlockingStub blockingStub;
    private final ConnectionConfig config;
    private final OkHttpClient httpClient;

    public Filesystem(Sandbox sandbox, ManagedChannel channel, ConnectionConfig config) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox cannot be null");
        this.blockingStub = FilesystemGrpc.newBlockingStub(channel).withMaxInboundMessageSize(32 * 1024 * 1024)
            .withMaxOutboundMessageSize(32 * 1024 * 1024);
        this.config = Objects.requireNonNull(config, "ConnectionConfig cannot be null");

        this.httpClient = new OkHttpClient.Builder().connectTimeout(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS).build();
    }

    /**
     * List entries in a directory.
     *
     * @param path path to the directory
     * @return list of entries in the directory
     */
    public List<EntryInfo> listDir(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        return listDir(path, 1);
    }

    /**
     * List entries in a directory with specified depth.
     *
     * @param path  path to the directory
     * @param depth depth of the directory to list
     * @return list of entries in the directory
     */
    public List<EntryInfo> listDir(String path, int depth) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (depth < 1) {
            throw new IllegalArgumentException("Depth must be at least 1");
        }

        try {
            ListDirRequest params = ListDirRequest.newBuilder().setPath(path).setDepth(depth).build();

            Request request = config.buildHttpRequest(SERVICE_NAME, "ListDir", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("ListDir HTTP request failed: " + response.code() + " " + response.message());
            }

            Reader reader = response.body().charStream();
            // Parse the JSON response back to protobuf
            ListDirResponse.Builder builder = ListDirResponse.newBuilder();
            PROTO_PARSER.merge(reader, builder);
            ListDirResponse listDirResponse = builder.build();

            List<EntryInfo> entries = new ArrayList<>(listDirResponse.getEntriesCount());
            for (io.openkruise.agents.client.e2b.envd.filesystem.EntryInfo entry :
                listDirResponse.getEntriesList()) {
                entries.add(toEntryInfo(entry));
            }
            return entries;
        } catch (Exception e) {
            throw new RuntimeException("Failed to listDir", e);
        }
    }

    /**
     * Create a new directory and all directories along the way if needed on the specified path.
     *
     * @param path path to a new directory
     * @return true if the directory was created, false if it already exists
     */
    public boolean makeDir(String path) {
        try {
            MakeDirRequest params = MakeDirRequest.newBuilder()
                .setPath(path)
                .build();

            Request request = config.buildHttpRequest(SERVICE_NAME, "MakeDir", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("MakeDir HTTP request failed: " + response.code() + " " + response.message());
            }
            return response.isSuccessful();
        } catch (Exception e) {
            throw new RuntimeException("Failed to makeDir", e);
        }
    }

    /**
     * Rename a file or directory.
     *
     * @param oldPath path to the file or directory to rename
     * @param newPath new path for the file or directory
     * @return information about renamed file or directory
     */
    public boolean move(String oldPath, String newPath) {
        try {
            MoveRequest params = MoveRequest.newBuilder()
                .setSource(oldPath)
                .setDestination(newPath)
                .build();

            Request request = config.buildHttpRequest(SERVICE_NAME, "Move", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Move HTTP request failed: " + response.code() + " " + response.message());
            }

            return response.isSuccessful();
        } catch (Exception e) {
            throw new RuntimeException("Failed to move", e);
        }
    }

    /**
     * Remove a file or directory.
     *
     * @param path path to a file or directory
     */
    public boolean remove(String path) {
        try {
            RemoveRequest params = RemoveRequest.newBuilder()
                .setPath(path)
                .build();

            Request request = config.buildHttpRequest(SERVICE_NAME, "Remove", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Remove HTTP request failed: " + response.code() + " " + response.message());
            }
            return response.isSuccessful();
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove", e);
        }
    }

    /**
     * Check if a file or a directory exists.
     *
     * @param path path to a file or a directory
     * @return true if the file or directory exists, false otherwise
     */
    public boolean exists(String path) {
        try {
            StatRequest params = StatRequest.newBuilder()
                .setPath(path)
                .build();

            Request request = config.buildHttpRequest(SERVICE_NAME, "Stat", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                return false;
            }
            if (!response.isSuccessful()) {
                throw new IOException("Stat HTTP request failed: " + response.code() + " " + response.message());
            }
            return response.isSuccessful();
        } catch (Exception e) {
            throw new RuntimeException("Failed to exists", e);
        }
    }

    /**
     * Get information about a file or directory.
     *
     * @param path path to a file or directory
     * @return information about the file or directory
     */
    public EntryInfo getInfo(String path) {
        try {
            StatRequest params = StatRequest.newBuilder()
                .setPath(path)
                .build();

            Request request = config.buildHttpRequest(SERVICE_NAME, "Stat", params, sandbox.getSandboxID());

            // noinspection WithSSRFCheckingInspection
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Stat HTTP request failed: " + response.code() + " " + response.message());
            }

            Reader reader = response.body().charStream();
            // Parse the JSON response back to protobuf
            StatResponse.Builder builder = StatResponse.newBuilder();
            PROTO_PARSER.merge(reader, builder);
            StatResponse statResponse = builder.build();

            return toEntryInfo(statResponse.getEntry());
        } catch (Exception e) {
            throw new RuntimeException("Failed to Remove", e);
        }
    }

    /**
     * Start watching a directory for filesystem events.
     *
     * @param path    path to directory to watch
     * @param onEvent callback to call when an event in the directory occurs
     * @return WatchHandle object for stopping watching directory
     */
    public WatchHandle watchDir(String path, Consumer<WatchHandle.FilesystemEvent> onEvent) {
        return watchDir(path, false, onEvent);
    }

    /**
     * Start watching a directory for filesystem events.
     *
     * @param path      path to directory to watch
     * @param recursive whether to watch subdirectories recursively
     * @param onEvent   callback to call when an event in the directory occurs
     * @return WatchHandle object for stopping watching directory
     */
    public WatchHandle watchDir(String path, boolean recursive, Consumer<WatchHandle.FilesystemEvent> onEvent) {
        WatchDirRequest request = WatchDirRequest.newBuilder().setPath(path).setRecursive(recursive).build();

        try {
            // This is a blocking stub, so we'll need to implement the streaming logic differently
            // For now, we'll create a streaming stub for this specific operation
            FilesystemGrpc.FilesystemStub streamingStub = FilesystemGrpc.newStub(blockingStub.getChannel());

            // Create a stream observer to handle responses
            io.grpc.stub.StreamObserver<WatchDirResponse> responseObserver = new WatchDirResponseObserver(onEvent);

            streamingStub.withDeadlineAfter(config.getRequestTimeoutMs(), TimeUnit.MILLISECONDS).watchDir(request,
                responseObserver);

            return new WatchHandle(responseObserver);
        } catch (StatusRuntimeException e) {
            throw RpcUtils.handleRpcException(e);
        }
    }

    private EntryInfo toEntryInfo(io.openkruise.agents.client.e2b.envd.filesystem.EntryInfo entry) {
        return new EntryInfo(entry.getName(), mapFileType(entry.getType()), entry.getPath(), entry.getSize(),
            entry.getMode(), entry.getPermissions(), entry.getOwner(), entry.getGroup(),
            entry.hasModifiedTime() ? entry.getModifiedTime() : null,
            entry.hasSymlinkTarget() ? entry.getSymlinkTarget() : null);
    }

    private FileType mapFileType(io.openkruise.agents.client.e2b.envd.filesystem.FileType fileType) {
        switch (fileType) {
            case FILE_TYPE_DIRECTORY:
                return FileType.DIR;
            case FILE_TYPE_FILE:
                return FileType.FILE;
            case FILE_TYPE_SYMLINK:
                return FileType.SYMLINK;
            default:
                throw new SandboxException("Unknown file type: " + fileType);
        }
    }

    public enum FileType {
        FILE,
        DIR,
        SYMLINK
    }

    public static class EntryInfo {
        private final String name;
        private final FileType type;
        private final String path;
        private final long size;
        private final int mode;
        private final String permissions;
        private final String owner;
        private final String group;
        private final com.google.protobuf.Timestamp modifiedTime; // 使用正确的Timestamp类型
        private final String symlinkTarget;

        public EntryInfo(String name, FileType type, String path, long size, int mode, String permissions, String owner,
            String group, com.google.protobuf.Timestamp modifiedTime, String symlinkTarget) {
            this.name = name;
            this.type = type;
            this.path = path;
            this.size = size;
            this.mode = mode;
            this.permissions = permissions;
            this.owner = owner;
            this.group = group;
            this.modifiedTime = modifiedTime;
            this.symlinkTarget = symlinkTarget;
        }

        // Getters
        public String getName() {return name;}

        public FileType getType() {return type;}

        public String getPath() {return path;}

        public long getSize() {return size;}

        public int getMode() {return mode;}

        public String getPermissions() {return permissions;}

        public String getOwner() {return owner;}

        public String getGroup() {return group;}

        public com.google.protobuf.Timestamp getModifiedTime() {return modifiedTime;} // 更准确的类型

        public String getSymlinkTarget() {return symlinkTarget;}

        @Override
        public String toString() {
            return String.format("EntryInfo{name='%s', type=%s, path='%s', size=%d}", name, type, path, size);
        }
    }
}
