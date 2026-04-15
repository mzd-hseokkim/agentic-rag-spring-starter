package kr.co.mz.agenticai.core.ingestion.normalize;

import java.text.Normalizer;
import org.springframework.ai.document.Document;

/**
 * Applies Unicode NFC composition to document text.
 *
 * <p>PDFs and some OCR outputs emit decomposed (NFD) Hangul — "한" stored as
 * "ㅎ+ㅏ+ㄴ" — which breaks tokenization, BM25 matching, and display.
 * Composing to NFC up-front makes the rest of the pipeline
 * representation-invariant.
 */
public final class TextNormalizer {

    private final boolean nfcEnabled;

    public TextNormalizer() {
        this(true);
    }

    public TextNormalizer(boolean nfcEnabled) {
        this.nfcEnabled = nfcEnabled;
    }

    public Document normalize(Document document) {
        if (!nfcEnabled) {
            return document;
        }
        String text = document.getText();
        if (text == null || text.isEmpty()) {
            return document;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        if (normalized.equals(text)) {
            return document;
        }
        return new Document(document.getId(), normalized, document.getMetadata());
    }
}
