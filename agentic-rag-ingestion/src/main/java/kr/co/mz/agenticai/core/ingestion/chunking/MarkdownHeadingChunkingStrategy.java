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

    public static final String CANONICAL_NAME = "markdown-heading";

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})[ \\t]++(\\S.*?)[ \\t]*+$");

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
        return CANONICAL_NAME;
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

        State state = new State();
        for (String line : text.split("\n", -1)) {
            maybeCloseChunkOnHeading(document, line, state);
            appendLine(state, line);
        }
        flushPending(document, state);
        return state.chunks;
    }

    /**
     * If {@code line} is a heading at or above {@code maxHeadingLevel}, close
     * the pending chunk and update the heading breadcrumb stack.
     */
    private void maybeCloseChunkOnHeading(Document document, String line, State state) {
        Matcher m = HEADING.matcher(line);
        if (!m.matches()) {
            return;
        }
        int level = m.group(1).length();
        if (level > maxHeadingLevel) {
            return;
        }
        if (!state.current.isEmpty()) {
            state.chunks.add(newChunk(document, state.current.toString(), state.headingStack, state.index++));
            state.current.setLength(0);
        }
        updateHeadingStack(state.headingStack, level, m.group(2).trim());
    }

    private static void updateHeadingStack(List<String> stack, int level, String heading) {
        while (stack.size() >= level) {
            stack.remove(stack.size() - 1);
        }
        while (stack.size() < level - 1) {
            stack.add("");
        }
        stack.add(heading);
    }

    private static void appendLine(State state, String line) {
        if (!state.current.isEmpty()) {
            state.current.append('\n');
        }
        state.current.append(line);
    }

    private void flushPending(Document document, State state) {
        if (!state.current.isEmpty()) {
            state.chunks.add(newChunk(document, state.current.toString(), state.headingStack, state.index));
        }
    }

    private static final class State {
        final List<Document> chunks = new ArrayList<>();
        final List<String> headingStack = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int index = 0;
    }

    private Document newChunk(Document parent, String text, List<String> headingStack, int index) {
        Map<String, Object> metadata = new HashMap<>(parent.getMetadata());
        metadata.put(ChunkMetadata.PARENT_DOCUMENT_ID, parent.getId());
        metadata.put(ChunkMetadata.CHUNK_INDEX, index);
        metadata.put(ChunkMetadata.CHUNK_STRATEGY, CANONICAL_NAME);
        metadata.put(ChunkMetadata.HEADING_PATH, String.join(" > ", headingStack));
        return new Document(text, metadata);
    }
}
