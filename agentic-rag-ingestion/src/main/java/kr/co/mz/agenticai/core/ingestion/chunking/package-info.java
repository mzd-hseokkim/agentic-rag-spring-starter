/**
 * Built-in {@link kr.co.mz.agenticai.core.common.spi.ChunkingStrategy}
 * implementations that do not require an embedding model:
 * fixed-size, recursive-character, and markdown-heading.
 *
 * <p>The embedding-driven {@code SemanticChunkingStrategy} lives in a
 * separate package because it needs an {@code EmbeddingModel} bean.
 */
package kr.co.mz.agenticai.core.ingestion.chunking;
