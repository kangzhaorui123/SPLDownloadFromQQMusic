// ApiException.java
package com.kenny.spldownloader.network;

public class ApiException extends Exception {
    private final boolean shouldRetry;

    public ApiException(String message) {
        this(message, false);
    }

    public ApiException(String message, boolean shouldRetry) {
        super(message);
        this.shouldRetry = shouldRetry;
    }

    public ApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public ApiException(String message, Throwable cause, boolean shouldRetry) {
        super(message, cause);
        this.shouldRetry = shouldRetry;
    }

    public boolean shouldRetry() {
        return shouldRetry;
    }

    public boolean isNetworkError() {
        String message = getMessage();
        return message != null && (
                message.contains("HTTP") ||
                        message.contains("连接") ||
                        message.contains("timeout") ||
                        message.contains("Network")
        );
    }
}