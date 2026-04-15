package kr.co.mz.agenticai.core.common.exception;

/** Thrown when retrieval, query transformation, or reranking fails. */
public class RetrievalException extends AgenticRagException {

    public RetrievalException(String message) {
        super(message);
    }

    public RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
