package io.openkruise.agents.client.e2b.utils;

import io.grpc.*;

public class PathModifyingInterceptor implements ClientInterceptor {
    private final String sandboxId;
    private final int port;

    public PathModifyingInterceptor(String sandboxId, int port) {
        this.sandboxId = sandboxId;
        this.port = port;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method,
        CallOptions callOptions,
        Channel next) {

        // debug build path: kruise/{sandboxId}/{port}/method
        String newPath = String.format("kruise/%s/%d/%s",
            sandboxId, port, method.getFullMethodName());

        // create MethodDescriptor，update fullMethodName
        MethodDescriptor<ReqT, RespT> modifiedMethod = method.toBuilder()
            .setFullMethodName(newPath)
            .build();

        return next.newCall(modifiedMethod, callOptions);
    }
}

