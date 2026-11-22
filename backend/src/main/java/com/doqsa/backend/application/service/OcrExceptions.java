package com.doqsa.backend.application.service;

public final class OcrExceptions {
    private OcrExceptions() { }
    public static class NotFound extends RuntimeException {
        public NotFound() { super("OCR item not found"); }
    }
    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }
    public static class Retryable extends RuntimeException {
        public Retryable(String message) { super(message); }
        public Retryable(String message, Throwable cause) { super(message, cause); }
    }
}
