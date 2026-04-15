package kr.co.mz.agenticai.core.common.exception;

/** Thrown when a document cannot be read, chunked, embedded, or persisted. */
public class IngestionException extends AgenticRagException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
