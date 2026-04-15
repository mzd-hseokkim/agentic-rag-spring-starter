package kr.co.mz.agenticai.core.retrieval;

import kr.co.mz.agenticai.core.common.RagMetadataKeys;

/**
 * Metadata keys attached to retrieved chunks. Thin alias over
 * {@link RagMetadataKeys} — kept for retrieval-side readability.
 */
public final class RetrievalMetadata {

    public static final String BM25_SCORE = RagMetadataKeys.BM25_SCORE;
    public static final String VECTOR_SCORE = RagMetadataKeys.VECTOR_SCORE;
    public static final String FUSED_SCORE = RagMetadataKeys.FUSED_SCORE;
    public static final String RANK = RagMetadataKeys.RANK;

    private RetrievalMetadata() {}
}
