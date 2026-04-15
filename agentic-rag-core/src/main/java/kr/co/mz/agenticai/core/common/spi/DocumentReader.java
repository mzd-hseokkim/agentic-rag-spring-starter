package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

/**
 * Reads the contents of a resource into a list of Spring AI {@link Document}s.
 * Register a bean to add support for a new file format (docx, html, csv, ...).
 */
public interface DocumentReader {

    /** Whether this reader can handle the given resource. */
    boolean supports(Resource resource);

    /**
     * Read and parse the resource. Implementations should set a stable
     * {@code source} metadata entry on each document.
     */
    List<Document> read(Resource resource);
}
