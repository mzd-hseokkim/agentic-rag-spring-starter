package kr.co.mz.agenticai.core.ingestion.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class MarkdownDocumentReaderTest {

    private final MarkdownDocumentReader reader = new MarkdownDocumentReader();

    @Test
    void supportsByExtension() {
        assertThat(reader.supports(new ClassPathResource("sample.md"))).isTrue();
        assertThat(reader.supports(namedResource("x.MARKDOWN"))).isTrue();
        assertThat(reader.supports(new ClassPathResource("sample.pdf"))).isFalse();
    }

    @Test
    void rejectsResourceWithoutFilename() {
        Resource anon = new ByteArrayResource(new byte[] {0x23, 0x20, 0x41});
        assertThat(reader.supports(anon)).isFalse();
    }

    @Test
    void readsSampleMarkdownAndAttachesSource() {
        List<Document> docs = reader.read(new ClassPathResource("sample.md"));

        assertThat(docs).isNotEmpty();
        assertThat(docs).allSatisfy(d -> {
            assertThat(d.getMetadata()).containsEntry("contentType", "text/markdown");
            assertThat(d.getMetadata()).containsEntry("source", "sample.md");
        });
        // Spring AI's markdown reader may extract headings to metadata; just
        // verify that the body content (list items, prose) made it through.
        String combinedText = docs.stream().map(Document::getText).reduce("", String::concat);
        assertThat(combinedText).contains("하이브리드");
    }

    private static Resource namedResource(String name) {
        return new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }
}
