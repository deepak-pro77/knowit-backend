package com.knowitai.exception;

public class InvalidImageException extends RuntimeException {

    private final InvalidImageReason reason;

    public InvalidImageException(InvalidImageReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public InvalidImageException(InvalidImageReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public InvalidImageReason getReason() {
        return reason;
    }
}
