package kr.co.mz.agenticai.core.factcheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.co.mz.agenticai.core.common.Citation;
import kr.co.mz.agenticai.core.common.exception.AgenticRagException;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

/**
 * LLM-as-judge {@link FactChecker}. Asks the chat model to decide whether
 * the answer is supported by the provided source chunks and to return its
 * verdict as a JSON object.
 */
public final class LlmFactChecker implements FactChecker {

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private final ChatModel chatModel;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;
    private final double minConfidence;

    public LlmFactChecker(ChatModel chatModel) {
        this(chatModel, KoreanFactCheckPrompts.VERIFY, 0.5);
    }

    public LlmFactChecker(ChatModel chatModel, String promptTemplate, double minConfidence) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException("minConfidence must be in [0, 1]");
        }
        this.minConfidence = minConfidence;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public FactCheckResult check(FactCheckRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.sources().isEmpty()) {
            return new FactCheckResult(false, 0.0, List.of(),
                    "No source chunks supplied; cannot verify.", Map.of());
        }

        String rendered = promptTemplate
                .replace("{query}", nullToEmpty(request.originalQuery()))
                .replace("{answer}", request.answer())
                .replace("{sources}", formatSources(request.sources()));

        String raw;
        try {
            ChatResponse resp = chatModel.call(new Prompt(rendered));
            raw = resp.getResult().getOutput().getText();
        } catch (RuntimeException e) {
            throw new AgenticRagException("FactChecker chat call failed", e);
        }
        if (raw == null || raw.isBlank()) {
            return new FactCheckResult(false, 0.0, List.of(),
                    "Empty response from LLM judge.", Map.of());
        }

        Verdict verdict = parseVerdict(raw);
        boolean grounded = verdict.grounded() && verdict.confidence() >= minConfidence;
        List<Citation> citations = collectCitations(request.sources(), verdict.supportingSourceIndexes());
        return new FactCheckResult(
                grounded, verdict.confidence(), citations,
                verdict.reason() == null ? "" : verdict.reason(),
                Map.of("rawVerdictGrounded", verdict.grounded()));
    }

    private Verdict parseVerdict(String raw) {
        String json = raw.trim();
        try {
            return objectMapper.readValue(json, Verdict.class);
        } catch (Exception ignored) {
            // Fall through to fenced extraction.
        }
        Matcher m = JSON_OBJECT.matcher(raw);
        if (m.find()) {
            try {
                return objectMapper.readValue(m.group(), Verdict.class);
            } catch (Exception e) {
                throw new AgenticRagException("FactChecker could not parse JSON verdict: " + raw, e);
            }
        }
        throw new AgenticRagException("FactChecker response did not contain a JSON object: " + raw);
    }

    private static List<Citation> collectCitations(List<Document> sources, List<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return List.of();
        }
        List<Citation> out = new ArrayList<>(indexes.size());
        for (Integer idx : indexes) {
            if (idx == null || idx < 0 || idx >= sources.size()) {
                continue;
            }
            out.add(Citation.fromDocument(sources.get(idx)));
        }
        return out;
    }

    private static String formatSources(List<Document> sources) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            Document d = sources.get(i);
            String src = String.valueOf(d.getMetadata().getOrDefault("source", d.getId()));
            sb.append('[').append(i).append("] (source: ").append(src).append(")\n");
            sb.append(d.getText() == null ? "" : d.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verdict(
            boolean grounded,
            double confidence,
            String reason,
            List<Integer> supportingSourceIndexes) {}
}
