/**
 * Retrieval layer: vector, BM25, hybrid-fusion search, query transformation,
 * and reranking.
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code bm25} — in-memory Lucene BM25 index</li>
 *   <li>{@code fusion} — result fusion strategies (RRF, weighted sum)</li>
 *   <li>{@code rerank} — {@code Reranker} implementations</li>
 * </ul>
 */
package kr.co.mz.agenticai.core.retrieval;
