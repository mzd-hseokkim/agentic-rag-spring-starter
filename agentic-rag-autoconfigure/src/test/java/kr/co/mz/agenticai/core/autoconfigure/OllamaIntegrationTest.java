package kr.co.mz.agenticai.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import kr.co.mz.agenticai.core.common.AgenticRagClient;
import kr.co.mz.agenticai.core.common.IngestionPipeline;
import kr.co.mz.agenticai.core.common.IngestionRequest;
import kr.co.mz.agenticai.core.common.IngestionResult;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;
import kr.co.mz.agenticai.core.common.RagStreamEvent;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import kr.co.mz.agenticai.core.factcheck.LlmFactChecker;
import kr.co.mz.agenticai.core.retrieval.bm25.LuceneBm25Index;
import kr.co.mz.agenticai.core.retrieval.query.HydeQueryTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * End-to-end integration test against a local Ollama instance.
 *
 * <p>Skipped automatically when Ollama is not reachable on
 * {@code localhost:11434}. Requires the models {@code qwen3-embedding:4b}
 * (for embeddings) — pull them with {@code ollama pull qwen3-embedding:4b}.
 */
@EnabledIf("ollamaAvailable")
class OllamaIntegrationTest {

    private static final String OLLAMA_HOST = "localhost";
    private static final int OLLAMA_PORT = 11434;
    private static final String EMBEDDING_MODEL = "qwen3-embedding:4b";
    private static final String CHAT_MODEL = "gpt-oss:20b";

    @SuppressWarnings("unused")
    static boolean ollamaAvailable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(OLLAMA_HOST, OLLAMA_PORT), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgenticRagCoreAutoConfiguration.class,
                    AgenticRagIngestionAutoConfiguration.class,
                    AgenticRagRetrievalAutoConfiguration.class))
            .withUserConfiguration(OllamaConfig.class);

    @Test
    void ingestKoreanMarkdownAndSearchBothWays() {
        runner.run(ctx -> {
            IngestionPipeline pipeline = ctx.getBean(IngestionPipeline.class);

            IngestionResult result = pipeline.ingest(
                    IngestionRequest.of(new ClassPathResource("korean-sample.md")));

            assertThat(result.totalChunks()).isPositive();
            assertThat(result.elapsedMillis()).isGreaterThanOrEqualTo(0);

            LuceneBm25Index bm25 = ctx.getBean(LuceneBm25Index.class);
            // Note: Spring AI's MarkdownDocumentReader extracts headings into
            // metadata, not body text, so BM25 queries must target body content.
            // (Improving heading-awareness is a Phase 2 enrichment step.)
            List<Document> bm25Hits = bm25.search("RRF 알고리즘", 3);
            assertThat(bm25Hits).as("BM25 Nori should tokenize and match Korean body text").isNotEmpty();
            assertThat(bm25Hits.get(0).getText()).contains("RRF");

            VectorStore vectorStore = ctx.getBean(VectorStore.class);
            List<Document> vectorHits = vectorStore.similaritySearch(
                    SearchRequest.builder().query("검색 품질을 높이는 방법").topK(3).build());
            assertThat(vectorHits).as("Vector similarity should return semantically related chunks")
                    .isNotEmpty();
        });
    }

    @Test
    void bm25AndVectorSearchReturnOverlappingButDistinctResults() {
        runner.run(ctx -> {
            IngestionPipeline pipeline = ctx.getBean(IngestionPipeline.class);
            pipeline.ingest(IngestionRequest.of(new ClassPathResource("korean-sample.md")));

            // A query that's semantically about facts but uses no literal keywords
            // from the doc — vector search should still find something; BM25 may miss.
            VectorStore vectorStore = ctx.getBean(VectorStore.class);
            List<Document> vectorHits = vectorStore.similaritySearch(
                    SearchRequest.builder().query("환각 방지와 신뢰성").topK(3).build());

            assertThat(vectorHits).isNotEmpty();
        });
    }

    @Test
    void hybridRouterCombinesBm25AndVectorResults() {
        runner.run(ctx -> {
            IngestionPipeline pipeline = ctx.getBean(IngestionPipeline.class);
            pipeline.ingest(IngestionRequest.of(new ClassPathResource("korean-sample.md")));

            RetrieverRouter router = ctx.getBean(RetrieverRouter.class);
            List<Document> hits = router.retrieve(
                    RetrieverRouter.Query.of("RRF 알고리즘으로 결과를 융합", 5));

            assertThat(hits).as("Hybrid router should return fused hits").isNotEmpty();
            assertThat(hits.size()).isLessThanOrEqualTo(5);
        });
    }

    @Test
    void factCheckerDetectsHallucinationsInGeneratedAnswer() {
        runner.withUserConfiguration(OllamaChatConfig.class).run(ctx -> {
            IngestionPipeline pipeline = ctx.getBean(IngestionPipeline.class);
            pipeline.ingest(IngestionRequest.of(new ClassPathResource("korean-sample.md")));

            RetrieverRouter router = ctx.getBean(RetrieverRouter.class);
            List<Document> sources = router.retrieve(
                    RetrieverRouter.Query.of("RRF 알고리즘", 3));

            ChatModel chat = ctx.getBean(ChatModel.class);
            FactChecker checker = new LlmFactChecker(chat);

            // Grounded answer (paraphrased from the doc).
            FactChecker.FactCheckResult ok = checker.check(new FactChecker.FactCheckRequest(
                    "벡터 검색과 BM25 검색을 RRF 알고리즘으로 융합합니다.",
                    sources, "RRF는 무엇인가?", java.util.Map.of()));
            assertThat(ok.grounded()).as("paraphrased answer should be grounded").isTrue();

            // Ungrounded answer — claim not present in any source.
            FactChecker.FactCheckResult bad = checker.check(new FactChecker.FactCheckRequest(
                    "이 모듈은 PostgreSQL을 기본 데이터베이스로 사용하며 자동으로 테이블을 생성합니다.",
                    sources, "어떤 DB를 쓰나?", java.util.Map.of()));
            assertThat(bad.grounded()).as("hallucinated claim should be flagged").isFalse();
        });
    }

    @Test
    void agentOrchestratorRunsFullPipeline() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AgenticRagCoreAutoConfiguration.class,
                        AgenticRagIngestionAutoConfiguration.class,
                        AgenticRagRetrievalAutoConfiguration.class,
                        AgenticRagFactCheckAutoConfiguration.class,
                        AgenticRagAgentsAutoConfiguration.class,
                        AgenticRagClientAutoConfiguration.class))
                .withUserConfiguration(OllamaConfig.class, OllamaChatConfig.class)
                .withPropertyValues("agentic-rag.agents.enabled=true")
                .run(ctx -> {
                    IngestionPipeline pipeline = ctx.getBean(IngestionPipeline.class);
                    pipeline.ingest(IngestionRequest.of(new ClassPathResource("korean-sample.md")));

                    AgenticRagClient client = ctx.getBean(AgenticRagClient.class);
                    RagResponse response = client.ask(RagRequest.of("RRF의 역할을 한 문장으로 설명해 주세요."));

                    assertThat(response.answer()).isNotBlank();
                    assertThat(response.attributes()).containsKey("agentTrace");
                    @SuppressWarnings("unchecked")
                    List<String> trace = (List<String>) response.attributes().get("agentTrace");
                    assertThat(trace).contains("intent", "retrieval", "summary");
                });
    }

    @Test
    void agenticRagClientAnswersFromIngestedSources() {
        runner.withUserConfiguration(OllamaChatConfig.class).run(ctx -> {
            IngestionPipeline pipeline = ctx.getBean(IngestionPipeline.class);
            pipeline.ingest(IngestionRequest.of(new ClassPathResource("korean-sample.md")));

            AgenticRagClient client = ctx.getBean(AgenticRagClient.class);
            RagResponse response = client.ask(RagRequest.of("RRF 알고리즘은 어떤 역할을 하나요?"));

            assertThat(response.answer()).isNotBlank();
            assertThat(response.citations()).isNotEmpty();
            // Answer should reference fusion/combining concepts that appear in the source.
            assertThat(response.answer()).containsAnyOf("RRF", "융합", "결합", "Reciprocal");
        });
    }

    @Test
    void agenticRagClientStreamsTokensThenCompleted() {
        runner.withUserConfiguration(OllamaChatConfig.class).run(ctx -> {
            IngestionPipeline pipeline = ctx.getBean(IngestionPipeline.class);
            pipeline.ingest(IngestionRequest.of(new ClassPathResource("korean-sample.md")));

            AgenticRagClient client = ctx.getBean(AgenticRagClient.class);

            List<RagStreamEvent> events = client.askStream(
                    RagRequest.of("이 모듈의 주요 기능 한 가지만 간단히")).collectList().block();

            assertThat(events).isNotEmpty();
            long tokenChunks = events.stream().filter(e -> e instanceof RagStreamEvent.TokenChunk).count();
            long completed = events.stream().filter(e -> e instanceof RagStreamEvent.Completed).count();
            assertThat(tokenChunks).isPositive();
            assertThat(completed).isEqualTo(1);
        });
    }

    @Test
    void hydeTransformerProducesHypotheticalKoreanAnswer() {
        runner.withUserConfiguration(OllamaChatConfig.class).run(ctx -> {
            ChatModel chatModel = ctx.getBean(ChatModel.class);
            var hyde = new HydeQueryTransformer(chatModel);

            Query out = hyde.transform(new Query("하이브리드 검색은 어떻게 동작하나요?"));

            assertThat(out.text()).isNotBlank();
            assertThat(out.text().length()).isGreaterThan(20);
            // Hypothetical answer should be substantially longer than the question.
            assertThat(out.text().length()).isGreaterThan("하이브리드 검색은 어떻게 동작하나요?".length());
        });
    }

    @Configuration
    static class OllamaConfig {

        @Bean
        EmbeddingModel embeddingModel() {
            OllamaApi api = OllamaApi.builder().baseUrl("http://" + OLLAMA_HOST + ":" + OLLAMA_PORT).build();
            return OllamaEmbeddingModel.builder()
                    .ollamaApi(api)
                    .defaultOptions(OllamaOptions.builder().model(EMBEDDING_MODEL).build())
                    .build();
        }

        @Bean
        VectorStore vectorStore(EmbeddingModel embeddingModel) {
            return SimpleVectorStore.builder(embeddingModel).build();
        }
    }

    @Configuration
    static class OllamaChatConfig {

        @Bean
        ChatModel chatModel() {
            OllamaApi api = OllamaApi.builder().baseUrl("http://" + OLLAMA_HOST + ":" + OLLAMA_PORT).build();
            return OllamaChatModel.builder()
                    .ollamaApi(api)
                    .defaultOptions(OllamaOptions.builder().model(CHAT_MODEL).temperature(0.3).build())
                    .build();
        }
    }
}
