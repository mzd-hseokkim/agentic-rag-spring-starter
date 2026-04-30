package kr.co.mz.agenticai.demo;

import java.util.Objects;
import kr.co.mz.agenticai.core.common.AgenticRagClient;
import kr.co.mz.agenticai.core.common.IngestionPipeline;
import kr.co.mz.agenticai.core.common.IngestionRequest;
import kr.co.mz.agenticai.core.common.IngestionResult;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;
import kr.co.mz.agenticai.core.common.RagStreamEvent;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class RagController {

    private final IngestionPipeline ingestion;
    private final AgenticRagClient client;

    public RagController(IngestionPipeline ingestion, AgenticRagClient client) {
        this.ingestion = ingestion;
        this.client = client;
    }

    /**
     * Multipart file upload — this is the real file-upload path. The
     * original filename is preserved so {@code DocumentReader.supports()}
     * can pick the right reader by extension (.md, .pdf, ...).
     */
    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<IngestionResult> ingest(@RequestPart("file") FilePart file) {
        return DataBufferUtils.join(file.content())
                .map(RagController::toByteArray)
                .publishOn(Schedulers.boundedElastic())
                .map(bytes -> ingestion.ingest(IngestionRequest.of(
                        new NamedByteArrayResource(bytes, file.filename()))));
    }

    /** Ingest content fetched from an HTTP(S) URL. */
    @PostMapping("/ingest/url")
    public Mono<IngestionResult> ingestUrl(@RequestBody UrlBody body) {
        return Mono.fromCallable(() -> ingestion.ingest(
                        IngestionRequest.of(new UrlResource(body.url()))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Synchronous RAG ask. {@code sessionId} (optional) drives the memory store. */
    @PostMapping("/ask")
    public Mono<RagResponse> ask(@RequestBody AskBody body) {
        RagRequest req = RagRequest.builder()
                .query(body.query())
                .sessionId(body.sessionId())
                .build();
        return Mono.fromCallable(() -> client.ask(req))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Server-Sent Events: token-level stream + final response. */
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<RagStreamEvent> stream(@RequestParam String query) {
        return client.askStream(RagRequest.of(query));
    }

    private static byte[] toByteArray(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return bytes;
    }

    /** Preserves the uploaded filename so readers can match by extension. */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof NamedByteArrayResource r
                            && super.equals(other)
                            && Objects.equals(this.filename, r.filename));
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), filename);
        }
    }

    public record AskBody(String query, String sessionId) {}

    public record UrlBody(String url) {
        public UrlBody {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("url must not be empty");
            }
        }
    }
}
