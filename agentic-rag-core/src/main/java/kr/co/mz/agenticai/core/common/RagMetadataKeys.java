package kr.co.mz.agenticai.core.common;

/**
 * Well-known metadata keys attached to {@link org.springframework.ai.document.Document}
 * instances as they flow through the pipeline. Feature modules
 * ({@code ingestion}, {@code retrieval}) expose their own thin aliases
 * ({@code ChunkMetadata}, {@code RetrievalMetadata}) that delegate here.
 */
public final class RagMetadataKeys {

    // Chunking
    public static final String PARENT_DOCUMENT_ID = "agenticRag.parentDocumentId";
    public static final String CHUNK_INDEX = "agenticRag.chunkIndex";
    public static final String CHUNK_STRATEGY = "agenticRag.chunkStrategy";
    public static final String HEADING_PATH = "agenticRag.headingPath";

    // Retrieval
    public static final String BM25_SCORE = "agenticRag.bm25Score";
    public static final String VECTOR_SCORE = "agenticRag.vectorScore";
    public static final String FUSED_SCORE = "agenticRag.fusedScore";
    public static final String RANK = "agenticRag.rank";

    private RagMetadataKeys() {}
}
