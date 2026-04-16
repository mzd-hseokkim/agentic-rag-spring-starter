package kr.co.mz.agenticai.core.ingestion.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.exception.IngestionException;
import kr.co.mz.agenticai.core.common.spi.DocumentReader;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;

/**
 * {@link DocumentReader} for {@code .md} / {@code .markdown} resources,
 * adapting Spring AI's {@link org.springframework.ai.reader.markdown.MarkdownDocumentReader}.
 */
public final class MarkdownDocumentReader implements DocumentReader {

    private final MarkdownDocumentReaderConfig config;

    public MarkdownDocumentReader() {
        this(MarkdownDocumentReaderConfig.defaultConfig());
    }

    public MarkdownDocumentReader(MarkdownDocumentReaderConfig config) {
        this.config = config;
    }

    @Override
    public boolean supports(Resource resource) {
        String filename = resource == null ? null : resource.getFilename();
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    @Override
    public List<Document> read(Resource resource) {
        try {
            var reader = new org.springframework.ai.reader.markdown.MarkdownDocumentReader(resource, config);
            List<Document> documents = reader.get();
            return attachSource(documents, resource);
        } catch (RuntimeException e) {
            throw new IngestionException(
                    "Failed to read markdown resource: " + safeDescription(resource), e);
        }
    }

    private List<Document> attachSource(List<Document> documents, Resource resource) {
        String source = safeDescription(resource);
        List<Document> enriched = new ArrayList<>(documents.size());
        for (Document d : documents) {
            Map<String, Object> metadata = new HashMap<>(d.getMetadata());
            metadata.putIfAbsent("source", source);
            metadata.putIfAbsent("contentType", "text/markdown");
            enriched.add(new Document(d.getId(), java.util.Objects.requireNonNullElse(d.getText(), ""), metadata));
        }
        return enriched;
    }

    private static String safeDescription(Resource resource) {
        if (resource == null) {
            return "<null>";
        }
        String filename = resource.getFilename();
        return filename != null ? filename : resource.getDescription();
    }
}
