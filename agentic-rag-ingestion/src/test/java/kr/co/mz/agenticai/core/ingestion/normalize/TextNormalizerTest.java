package kr.co.mz.agenticai.core.ingestion.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class TextNormalizerTest {

    @Test
    void composesDecomposedHangul() {
        // NFD form of "한글" — jamo decomposed.
        String nfd = Normalizer.normalize("한글", Normalizer.Form.NFD);
        assertThat(nfd).isNotEqualTo("한글"); // sanity: NFD differs from NFC

        Document input = new Document(nfd, Map.of());
        Document out = new TextNormalizer().normalize(input);

        assertThat(out.getText()).isEqualTo("한글");
    }

    @Test
    void returnsSameInstanceWhenAlreadyNfc() {
        Document input = new Document("already composed", Map.of());
        assertThat(new TextNormalizer().normalize(input)).isSameAs(input);
    }

    @Test
    void disabledBypassesNormalization() {
        String nfd = Normalizer.normalize("한글", Normalizer.Form.NFD);
        Document input = new Document(nfd, Map.of());

        Document out = new TextNormalizer(false).normalize(input);

        assertThat(out.getText()).isEqualTo(nfd);
    }
}
