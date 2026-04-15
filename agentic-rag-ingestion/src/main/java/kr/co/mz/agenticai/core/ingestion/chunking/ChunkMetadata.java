package kr.co.mz.agenticai.core.ingestion.chunking;

import kr.co.mz.agenticai.core.common.RagMetadataKeys;

/**
 * Metadata keys attached to chunk documents. Thin alias over
 * {@link RagMetadataKeys} — kept for ingestion-side readability.
 */
public final class ChunkMetadata {

    public static final String PARENT_DOCUMENT_ID = RagMetadataKeys.PARENT_DOCUMENT_ID;
    public static final String CHUNK_INDEX = RagMetadataKeys.CHUNK_INDEX;
    public static final String CHUNK_STRATEGY = RagMetadataKeys.CHUNK_STRATEGY;
    public static final String HEADING_PATH = RagMetadataKeys.HEADING_PATH;

    private ChunkMetadata() {}
}
