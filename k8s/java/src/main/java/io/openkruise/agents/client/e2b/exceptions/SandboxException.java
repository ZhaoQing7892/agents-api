package io.openkruise.agents.client.e2b.exceptions;

public class SandboxException extends RuntimeException {
    public SandboxException(String message) {
        super(message);
    }

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}

