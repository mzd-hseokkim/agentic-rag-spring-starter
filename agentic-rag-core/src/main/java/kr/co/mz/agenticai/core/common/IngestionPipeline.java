package kr.co.mz.agenticai.core.common;

/** Public entry point for document ingestion. */
public interface IngestionPipeline {

    IngestionResult ingest(IngestionRequest request);
}
