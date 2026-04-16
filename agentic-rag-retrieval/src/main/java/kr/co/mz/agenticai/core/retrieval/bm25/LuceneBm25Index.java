package kr.co.mz.agenticai.core.retrieval.bm25;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import kr.co.mz.agenticai.core.common.exception.RetrievalException;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.ai.document.Document;

/**
 * In-memory BM25 index for Spring AI {@link Document}s backed by Lucene's
 * {@link ByteBuffersDirectory}.
 *
 * <p>Original documents are kept by id in a side map so metadata doesn't
 * need to be serialized into Lucene fields; only {@code id} and
 * {@code content} are indexed.
 *
 * <p>Intended usage is: build once from an ingestion batch, then serve many
 * searches. Concurrent writes are supported but incur a reader refresh on
 * the next search.
 */
public final class LuceneBm25Index implements AutoCloseable {

    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";

    private final Directory directory = new ByteBuffersDirectory();
    private final Analyzer analyzer;
    private final IndexWriter writer;
    private final SearcherManager searcherManager;
    private final Map<String, Document> idToSource = new ConcurrentHashMap<>();

    /** Uses {@link StandardAnalyzer} — suitable for English but not Korean. */
    public LuceneBm25Index() {
        this(new StandardAnalyzer());
    }

    /**
     * @param analyzer analyzer used both for indexing and query tokenization.
     *     For Korean text use {@code KoreanAnalyzers.standard()} instead of
     *     the default.
     */
    public LuceneBm25Index(Analyzer analyzer) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());
            this.writer = new IndexWriter(directory, config);
            this.writer.commit();
            this.searcherManager = new SearcherManager(writer, true, true, null);
        } catch (IOException e) {
            throw new RetrievalException("Failed to initialize Lucene BM25 index", e);
        }
    }

    /** Index the given documents and refresh the searcher. */
    public void addDocuments(Collection<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            for (Document doc : documents) {
                if (doc.getText() == null) {
                    continue;
                }
                org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
                luceneDoc.add(new StringField(FIELD_ID, doc.getId(), Field.Store.YES));
                luceneDoc.add(new TextField(FIELD_CONTENT, doc.getText(), Field.Store.NO));
                writer.addDocument(luceneDoc);
                idToSource.put(doc.getId(), doc);
            }
            writer.commit();
            searcherManager.maybeRefresh();
        } catch (IOException e) {
            throw new RetrievalException("Failed to index documents", e);
        }
    }

    /**
     * Return the top-{@code k} documents for {@code queryText}, each enriched
     * with a BM25 score and zero-based rank in its metadata. The returned
     * documents are new {@link Document} instances; the originals are not
     * mutated.
     */
    public List<Document> search(String queryText, int topK) {
        if (queryText == null || queryText.isBlank() || topK <= 0) {
            return List.of();
        }

        IndexSearcher searcher = null;
        try {
            searcher = searcherManager.acquire();
            Query query = buildQuery(queryText);
            if (query == null) {
                return List.of();
            }
            TopDocs topDocs = searcher.search(query, topK);
            StoredFields storedFields = searcher.storedFields();

            List<Document> results = new ArrayList<>(topDocs.scoreDocs.length);
            for (int i = 0; i < topDocs.scoreDocs.length; i++) {
                ScoreDoc sd = topDocs.scoreDocs[i];
                org.apache.lucene.document.Document luceneDoc = storedFields.document(sd.doc);
                String id = luceneDoc.get(FIELD_ID);
                Document source = idToSource.get(id);
                if (source == null) {
                    continue;
                }
                results.add(enrich(source, sd.score, i));
            }
            return results;
        } catch (IOException e) {
            throw new RetrievalException("Failed to search Lucene BM25 index", e);
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (IOException ignored) {
                    // best-effort release
                }
            }
        }
    }

    private Query buildQuery(String text) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        int clauses = 0;
        try (TokenStream tokens = analyzer.tokenStream(FIELD_CONTENT, text)) {
            CharTermAttribute attr = tokens.addAttribute(CharTermAttribute.class);
            tokens.reset();
            while (tokens.incrementToken()) {
                builder.add(new TermQuery(new Term(FIELD_CONTENT, attr.toString())), Occur.SHOULD);
                clauses++;
            }
            tokens.end();
        } catch (IOException e) {
            throw new RetrievalException("Failed to tokenize query: " + text, e);
        }
        return clauses == 0 ? null : builder.build();
    }

    private Document enrich(Document source, float score, int rank) {
        Map<String, Object> metadata = new HashMap<>(source.getMetadata());
        metadata.put(RetrievalMetadata.BM25_SCORE, (double) score);
        metadata.put(RetrievalMetadata.RANK, rank);
        return new Document(source.getId(), Objects.requireNonNullElse(source.getText(), ""), metadata);
    }

    /** Number of indexed documents. */
    public int size() {
        return idToSource.size();
    }

    @Override
    public void close() {
        try {
            searcherManager.close();
        } catch (IOException ignored) {
            // best-effort
        }
        try {
            writer.close();
        } catch (IOException ignored) {
            // best-effort
        }
        try {
            directory.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
