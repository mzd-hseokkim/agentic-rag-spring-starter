package kr.co.mz.agenticai.demo;

import java.net.http.HttpClient;
import java.time.Duration;
import kr.co.mz.agenticai.core.common.IngestionPipeline;
import kr.co.mz.agenticai.core.common.IngestionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * In-memory {@link SimpleVectorStore} + startup ingest of bundled Korean
 * samples. Swap in a real vector store (pgvector, chroma, qdrant, ...) by
 * replacing this bean definition.
 */
@Configuration
public class DemoConfig {

    private static final Logger log = LoggerFactory.getLogger(DemoConfig.class);

    /**
     * Ollama's {@code /api/chat} is non-streaming from Spring AI's point of
     * view — a large model like {@code gpt-oss:20b} can take 30-60s on CPU.
     * Spring AI's default Netty-backed client ignores
     * {@code spring.http.client.read-timeout}, so we provide our own
     * {@link OllamaApi} backed by a JDK {@link HttpClient} with a generous
     * read timeout.
     */
    @Bean
    OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build());
        factory.setReadTimeout(Duration.ofMinutes(5));
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(factory);
        return OllamaApi.builder().baseUrl(baseUrl).restClientBuilder(restClientBuilder).build();
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    CommandLineRunner ingestSamples(IngestionPipeline pipeline) {
        return args -> {
            Resource[] samples = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:samples/*.md");
            if (samples.length == 0) {
                log.warn("No samples found under classpath:samples/*.md");
                return;
            }
            for (Resource r : samples) {
                try {
                    var result = pipeline.ingest(IngestionRequest.of(r));
                    log.info("Ingested '{}' → {} chunks in {} ms",
                            r.getFilename(), result.totalChunks(), result.elapsedMillis());
                } catch (RuntimeException e) {
                    log.error("Failed to ingest {}", r.getFilename(), e);
                }
            }
        };
    }
}
