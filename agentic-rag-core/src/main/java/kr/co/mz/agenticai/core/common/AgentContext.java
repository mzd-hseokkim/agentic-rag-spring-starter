package kr.co.mz.agenticai.core.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.document.Document;

/**
 * Mutable state shared across all {@code Agent}s during a single
 * orchestrator run. Agents read the slots they need and write the slots
 * they produce; the orchestrator drives the order.
 */
public final class AgentContext {

    private final RagRequest request;
    private final String correlationId;
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<String> trace = new ArrayList<>();

    private String intent;
    private String refinedQuery;
    private List<Document> retrieved = List.of();
    private List<Document> selectedSources = List.of();
    private String answer;
    private List<Citation> citations = List.of();
    private Boolean validationPassed;
    private String validationReason;
    private int iteration = 0;

    public AgentContext(RagRequest request, String correlationId) {
        this.request = Objects.requireNonNull(request, "request");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.refinedQuery = request.query();
    }

    public RagRequest request() { return request; }
    public String correlationId() { return correlationId; }
    public Map<String, Object> attributes() { return attributes; }
    public List<String> trace() { return trace; }

    public String intent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String refinedQuery() { return refinedQuery; }
    public void setRefinedQuery(String q) { this.refinedQuery = q; }

    public List<Document> retrieved() { return retrieved; }
    public void setRetrieved(List<Document> docs) { this.retrieved = docs == null ? List.of() : List.copyOf(docs); }

    public List<Document> selectedSources() { return selectedSources; }
    public void setSelectedSources(List<Document> docs) { this.selectedSources = docs == null ? List.of() : List.copyOf(docs); }

    public String answer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<Citation> citations() { return citations; }
    public void setCitations(List<Citation> c) { this.citations = c == null ? List.of() : List.copyOf(c); }

    public Boolean validationPassed() { return validationPassed; }
    public void setValidationPassed(Boolean v) { this.validationPassed = v; }

    public String validationReason() { return validationReason; }
    public void setValidationReason(String r) { this.validationReason = r; }

    public int iteration() { return iteration; }
    public void incrementIteration() { this.iteration++; }

    public void recordStep(String agentName) {
        trace.add(agentName);
    }
}
