package kr.co.mz.agenticai.core.ingestion.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.exception.IngestionException;
import kr.co.mz.agenticai.core.common.spi.DocumentReader;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;

/**
 * {@link DocumentReader} for {@code .pdf} resources, adapting Spring AI's
 * {@link PagePdfDocumentReader} (one {@code Document} per PDF page).
 */
public final class PdfDocumentReader implements DocumentReader {

    private final PdfDocumentReaderConfig config;

    public PdfDocumentReader() {
        this(PdfDocumentReaderConfig.defaultConfig());
    }

    public PdfDocumentReader(PdfDocumentReaderConfig config) {
        this.config = config;
    }

    @Override
    public boolean supports(Resource resource) {
        String filename = resource == null ? null : resource.getFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> read(Resource resource) {
        try {
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
            List<Document> documents = reader.get();
            return attachSource(documents, resource);
        } catch (RuntimeException e) {
            throw new IngestionException(
                    "Failed to read PDF resource: " + safeDescription(resource), e);
        }
    }

    private List<Document> attachSource(List<Document> documents, Resource resource) {
        String source = safeDescription(resource);
        List<Document> enriched = new ArrayList<>(documents.size());
        for (Document d : documents) {
            Map<String, Object> metadata = new HashMap<>(d.getMetadata());
            metadata.putIfAbsent("source", source);
            metadata.putIfAbsent("contentType", "application/pdf");
            enriched.add(new Document(d.getId(), d.getText(), metadata));
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
