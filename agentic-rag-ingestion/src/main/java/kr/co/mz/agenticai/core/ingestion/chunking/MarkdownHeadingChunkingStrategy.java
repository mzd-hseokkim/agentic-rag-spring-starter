package kr.co.mz.agenticai.core.ingestion.chunking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.co.mz.agenticai.core.common.spi.ChunkingStrategy;
import org.springframework.ai.document.Document;

/**
 * Splits a markdown document at ATX headings ({@code "# "}, {@code "## "}, ...)
 * up to a configurable maximum level. The heading breadcrumb is recorded on
 * each chunk under {@link ChunkMetadata#HEADING_PATH}.
 *
 * <p>This strategy does not further split long sections; combine with a
 * recursive strategy downstream if your source contains huge sections under
 * a single heading.
 */
public final class MarkdownHeadingChunkingStrategy implements ChunkingStrategy {

    public static final String NAME = "markdown-heading";

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");

    private final int maxHeadingLevel;

    public MarkdownHeadingChunkingStrategy(int maxHeadingLevel) {
        if (maxHeadingLevel < 1 || maxHeadingLevel > 6) {
            throw new IllegalArgumentException("maxHeadingLevel must be in [1, 6], was " + maxHeadingLevel);
        }
        this.maxHeadingLevel = maxHeadingLevel;
    }

    public MarkdownHeadingChunkingStrategy() {
        this(3);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(Document document) {
        Object contentType = document.getMetadata().get("contentType");
        if ("text/markdown".equals(contentType)) {
            return true;
        }
        Object source = document.getMetadata().get("source");
        return source instanceof String s && s.toLowerCase().endsWith(".md");
    }

    @Override
    public List<Document> chunk(Document document) {
        String text = document.getText();
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String line : text.split("\n", -1)) {
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                int level = m.group(1).length();
                if (level <= maxHeadingLevel) {
                    if (!current.isEmpty()) {
                        chunks.add(newChunk(document, current.toString(), headingStack, index++));
                        current.setLength(0);
                    }
                    while (headingStack.size() >= level) {
                        headingStack.remove(headingStack.size() - 1);
                    }
                    while (headingStack.size() < level - 1) {
                        headingStack.add("");
                    }
                    headingStack.add(m.group(2).trim());
                }
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }

        if (!current.isEmpty()) {
            chunks.add(newChunk(document, current.toString(), headingStack, index));
        }
        return chunks;
    }

    private Document newChunk(Document parent, String text, List<String> headingStack, int index) {
        Map<String, Object> metadata = new HashMap<>(parent.getMetadata());
        metadata.put(ChunkMetadata.PARENT_DOCUMENT_ID, parent.getId());
        metadata.put(ChunkMetadata.CHUNK_INDEX, index);
        metadata.put(ChunkMetadata.CHUNK_STRATEGY, NAME);
        metadata.put(ChunkMetadata.HEADING_PATH, String.join(" > ", headingStack));
        return new Document(text, metadata);
    }
}
