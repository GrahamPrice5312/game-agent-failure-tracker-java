package dev.learninggame.errors;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public InfraiException(String code, String message, int httpStatus) {
        super(message == null || message.isBlank() ? code : message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
