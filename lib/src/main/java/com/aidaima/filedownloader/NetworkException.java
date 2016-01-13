package com.aidaima.filedownloader;

public class NetworkException extends Exception {

    public NetworkException() {
        this("", null);
    }

    public NetworkException(String exceptionMessage) {
        this(exceptionMessage, null);
    }

    public NetworkException(String exceptionMessage, Throwable reason) {
        super(exceptionMessage, reason);
//        networkResponse = response;
    }

    public NetworkException(Throwable cause) {
        super(cause);
//        networkResponse = null;
    }

    /**
     * Indicates that the error responded with an error response.
     */
    @SuppressWarnings("serial")
    public static class ServerError extends NetworkException {

        public ServerError() {
            super();
        }
    }

    /**
     * Indicates that there was a network error when performing a request.
     */
    @SuppressWarnings("serial")
    public static class NetworkError extends NetworkException {
        public NetworkError() {
            super();
        }

        public NetworkError(Throwable reason) {
            super(reason);
        }

        public NetworkError(String exceptionMessage) {
            super(exceptionMessage, null);
        }
    }

    public static class NetworkCancel extends NetworkException {
        public NetworkCancel(String exceptionMessage) {
            super(exceptionMessage);
        }
    }

    /**
     * Indicates that the connection or the socket timed out.
     */
    @SuppressWarnings("serial")
    public static class TimeoutError extends NetworkException {
    }

    /**
     * Error indicating that no connection could be established when performing a request.
     */
    @SuppressWarnings("serial")
    public static class NoConnectionError extends NetworkError {
        public NoConnectionError() {
            super();
        }

        public NoConnectionError(Throwable reason) {
            super(reason);
        }
    }
}
