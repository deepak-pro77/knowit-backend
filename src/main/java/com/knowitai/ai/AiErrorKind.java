package com.knowitai.ai;

import org.springframework.http.HttpStatus;

/** Every way the AI call can fail, with the safe message the client sees. */
public enum AiErrorKind {
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is currently unavailable. Please try again later."),
    BUSY(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is busy right now. Please try again in a minute."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "The AI service took too long to respond. Please try again."),
    BAD_RESPONSE(HttpStatus.BAD_GATEWAY,
            "The AI returned an incomplete answer. Please try again, ideally with a clearer photo."),
    BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "The AI could not analyze this image. Please try a different photo.");

    private final HttpStatus status;
    private final String clientMessage;

    AiErrorKind(HttpStatus status, String clientMessage) {
        this.status = status;
        this.clientMessage = clientMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String clientMessage() {
        return clientMessage;
    }
}
