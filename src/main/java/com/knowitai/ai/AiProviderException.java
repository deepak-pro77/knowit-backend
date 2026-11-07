package com.knowitai.ai;

public class AiProviderException extends RuntimeException {

    private final AiErrorKind kind;

    /** 'detail' is for OUR logs only. The client receives kind.clientMessage(). */
    public AiProviderException(AiErrorKind kind, String detail, Throwable cause) {
        super(detail, cause);
        this.kind = kind;
    }

    public AiErrorKind getKind() {
        return kind;
    }
}
