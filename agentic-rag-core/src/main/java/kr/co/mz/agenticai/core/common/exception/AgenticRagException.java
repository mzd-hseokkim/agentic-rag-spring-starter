package kr.co.mz.agenticai.core.common.exception;

/** Base unchecked exception for all Agentic RAG failures. */
public class AgenticRagException extends RuntimeException {

    public AgenticRagException(String message) {
        super(message);
    }

    public AgenticRagException(String message, Throwable cause) {
        super(message, cause);
    }
}
