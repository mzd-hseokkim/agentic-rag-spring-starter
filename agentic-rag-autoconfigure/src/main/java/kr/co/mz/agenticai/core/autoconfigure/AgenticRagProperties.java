package kr.co.mz.agenticai.core.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties bound to the {@code agentic-rag.*} namespace. */
@ConfigurationProperties(prefix = "agentic-rag")
public class AgenticRagProperties {

    public enum Language { KO, EN }

    private boolean enabled = true;
    private Language language = Language.KO;
    private Ingestion ingestion = new Ingestion();
    private Retrieval retrieval = new Retrieval();
    private Factcheck factcheck = new Factcheck();
    private Client client = new Client();
    private Agents agents = new Agents();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = language; }

    public Ingestion getIngestion() { return ingestion; }
    public void setIngestion(Ingestion ingestion) { this.ingestion = ingestion; }

    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }

    public Factcheck getFactcheck() { return factcheck; }
    public void setFactcheck(Factcheck factcheck) { this.factcheck = factcheck; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public Agents getAgents() { return agents; }
    public void setAgents(Agents agents) { this.agents = agents; }

    public static class Ingestion {
        private boolean enabled = true;
        private boolean normalizeUnicode = true;
        private Chunking chunking = new Chunking();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isNormalizeUnicode() { return normalizeUnicode; }
        public void setNormalizeUnicode(boolean v) { this.normalizeUnicode = v; }

        public Chunking getChunking() { return chunking; }
        public void setChunking(Chunking chunking) { this.chunking = chunking; }
    }

    public static class Chunking {
        /** Explicit strategy name override; {@code null} = auto-select via {@code supports()}. */
        private String defaultStrategy;
        private FixedSize fixedSize = new FixedSize();
        private Recursive recursive = new Recursive();
        private MarkdownHeading markdownHeading = new MarkdownHeading();
        private Semantic semantic = new Semantic();

        public String getDefaultStrategy() { return defaultStrategy; }
        public void setDefaultStrategy(String s) { this.defaultStrategy = s; }

        public FixedSize getFixedSize() { return fixedSize; }
        public void setFixedSize(FixedSize v) { this.fixedSize = v; }

        public Recursive getRecursive() { return recursive; }
        public void setRecursive(Recursive v) { this.recursive = v; }

        public MarkdownHeading getMarkdownHeading() { return markdownHeading; }
        public void setMarkdownHeading(MarkdownHeading v) { this.markdownHeading = v; }

        public Semantic getSemantic() { return semantic; }
        public void setSemantic(Semantic v) { this.semantic = v; }
    }

    public static class FixedSize {
        private int maxChars = 1000;
        private int overlap = 200;
        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int v) { this.maxChars = v; }
        public int getOverlap() { return overlap; }
        public void setOverlap(int v) { this.overlap = v; }
    }

    public static class Recursive {
        private int maxChars = 1000;
        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int v) { this.maxChars = v; }
    }

    public static class MarkdownHeading {
        private int maxLevel = 3;
        public int getMaxLevel() { return maxLevel; }
        public void setMaxLevel(int v) { this.maxLevel = v; }
    }

    public static class Semantic {
        /** Disabled by default — requires an {@code EmbeddingModel} bean. */
        private boolean enabled = false;
        private double thresholdPercentile = 0.95;
        private int maxChunkChars = 2000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public double getThresholdPercentile() { return thresholdPercentile; }
        public void setThresholdPercentile(double v) { this.thresholdPercentile = v; }
        public int getMaxChunkChars() { return maxChunkChars; }
        public void setMaxChunkChars(int v) { this.maxChunkChars = v; }
    }

    public static class Retrieval {
        private boolean enabled = true;
        private int overscanFactor = 3;
        private Bm25 bm25 = new Bm25();
        private QueryTransform query = new QueryTransform();
        private Evaluator evaluator = new Evaluator();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getOverscanFactor() { return overscanFactor; }
        public void setOverscanFactor(int v) { this.overscanFactor = v; }
        public Bm25 getBm25() { return bm25; }
        public void setBm25(Bm25 v) { this.bm25 = v; }
        public QueryTransform getQuery() { return query; }
        public void setQuery(QueryTransform v) { this.query = v; }
        public Evaluator getEvaluator() { return evaluator; }
        public void setEvaluator(Evaluator v) { this.evaluator = v; }
    }

    public static class Evaluator {
        /** When false (default), a {@link kr.co.mz.agenticai.core.retrieval.evaluate.PassThroughRetrievalEvaluator} is used. */
        private boolean enabled = false;
        /** Minimum fused score [0.0, 1.0] for the score-threshold strategy. */
        private double minScore = 0.5;
        /** Evaluation strategy: {@code pass-through} or {@code score-threshold}. */
        private String strategy = "pass-through";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double v) { this.minScore = v; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String v) { this.strategy = v; }
    }

    public static class QueryTransform {
        private Toggle hyde = new Toggle();
        private Toggle rewrite = new Toggle();
        private MultiQuery multiQuery = new MultiQuery();
        public Toggle getHyde() { return hyde; }
        public void setHyde(Toggle v) { this.hyde = v; }
        public Toggle getRewrite() { return rewrite; }
        public void setRewrite(Toggle v) { this.rewrite = v; }
        public MultiQuery getMultiQuery() { return multiQuery; }
        public void setMultiQuery(MultiQuery v) { this.multiQuery = v; }
    }

    public static class Toggle {
        private boolean enabled = false;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static class MultiQuery {
        private boolean enabled = false;
        private int count = 3;
        private boolean includeOriginal = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getCount() { return count; }
        public void setCount(int v) { this.count = v; }
        public boolean isIncludeOriginal() { return includeOriginal; }
        public void setIncludeOriginal(boolean v) { this.includeOriginal = v; }
    }

    public static class Bm25 {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static class Factcheck {
        /** Disabled by default — opt-in once a {@code ChatModel} is available. */
        private boolean enabled = false;
        private double minConfidence = 0.5;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public double getMinConfidence() { return minConfidence; }
        public void setMinConfidence(double v) { this.minConfidence = v; }
    }

    public static class Client {
        /** Default top-K passed to the retriever when the request supplies none. */
        private int defaultTopK = 5;
        public int getDefaultTopK() { return defaultTopK; }
        public void setDefaultTopK(int v) { this.defaultTopK = v; }
    }

    public static class Agents {
        /** Disabled by default — opt-in to multi-agent orchestration. */
        private boolean enabled = false;
        private int maxIterations = 2;
        private int maxSources = 5;
        private Summary summary = new Summary();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int v) { this.maxIterations = v; }
        public int getMaxSources() { return maxSources; }
        public void setMaxSources(int v) { this.maxSources = v; }
        public Summary getSummary() { return summary; }
        public void setSummary(Summary v) { this.summary = v; }
    }

    public static class Summary {
        /** Override for {@code SummaryAgent}'s system prompt; null/blank uses the bundled Korean default. */
        private String systemPrompt;
        /** Override for {@code SummaryAgent}'s user prompt template; null/blank uses the bundled Korean default.
         * Custom values must contain both {@code {query}} and {@code {sources}} placeholders. */
        private String userPromptTemplate;
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String v) { this.systemPrompt = v; }
        public String getUserPromptTemplate() { return userPromptTemplate; }
        public void setUserPromptTemplate(String v) { this.userPromptTemplate = v; }
    }

    private Guardrails guardrails = new Guardrails();
    public Guardrails getGuardrails() { return guardrails; }
    public void setGuardrails(Guardrails v) { this.guardrails = v; }

    public static class Guardrails {
        private Toggle piiMask = new Toggle();
        private Toggle promptInjection = new Toggle();
        private Toggle logging = new Toggle();
        public Toggle getPiiMask() { return piiMask; }
        public void setPiiMask(Toggle v) { this.piiMask = v; }
        public Toggle getPromptInjection() { return promptInjection; }
        public void setPromptInjection(Toggle v) { this.promptInjection = v; }
        public Toggle getLogging() { return logging; }
        public void setLogging(Toggle v) { this.logging = v; }
    }

    private Memory memory = new Memory();
    public Memory getMemory() { return memory; }
    public void setMemory(Memory v) { this.memory = v; }

    private Tools tools = new Tools();
    public Tools getTools() { return tools; }
    public void setTools(Tools v) { this.tools = v; }

    public static class Tools {
        private boolean enabled = true;
        /** When non-empty, only tools whose name appears here are exposed. */
        private List<String> allowedNames = new ArrayList<>();
        /** Tools whose name appears here are excluded even if allowed. */
        private List<String> deniedNames = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public List<String> getAllowedNames() { return allowedNames; }
        public void setAllowedNames(List<String> v) {
            this.allowedNames = v == null ? new ArrayList<>() : v;
        }
        public List<String> getDeniedNames() { return deniedNames; }
        public void setDeniedNames(List<String> v) {
            this.deniedNames = v == null ? new ArrayList<>() : v;
        }
    }

    public static class Memory {
        private boolean enabled = true;
        /** Number of past messages prefixed to each LLM call. */
        private int historyLimit = 10;
        private Redis redis = new Redis();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getHistoryLimit() { return historyLimit; }
        public void setHistoryLimit(int v) { this.historyLimit = v; }
        public Redis getRedis() { return redis; }
        public void setRedis(Redis v) { this.redis = v; }
    }

    public static class Redis {
        /** Activates the Redis-backed store when Spring Data Redis is on the classpath. */
        private boolean enabled = true;
        private String keyPrefix = "agentic-rag:memory:";
        /** Per-conversation TTL refreshed on every append; null disables expiry. */
        private Duration ttl = Duration.ofHours(24);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String v) { this.keyPrefix = v; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration v) { this.ttl = v; }
    }

    private Observability observability = new Observability();
    public Observability getObservability() { return observability; }
    public void setObservability(Observability v) { this.observability = v; }

    public static class Observability {
        /** Active by default, but only takes effect when a {@code MeterRegistry} bean exists. */
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }
}
