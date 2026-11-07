package com.knowitai.exception;

import org.springframework.http.HttpStatus;

public enum InvalidImageReason {
    EMPTY_FILE(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    UNREADABLE(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    InvalidImageReason(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
