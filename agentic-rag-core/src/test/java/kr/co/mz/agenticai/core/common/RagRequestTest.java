package kr.co.mz.agenticai.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RagRequestTest {

    @Test
    void ofQuery_yieldsSensibleDefaults() {
        RagRequest req = RagRequest.of("what is RAG?");

        assertThat(req.query()).isEqualTo("what is RAG?");
        assertThat(req.sessionId()).isNull();
        assertThat(req.userId()).isNull();
        assertThat(req.streaming()).isFalse();
        assertThat(req.metadataFilters()).isEmpty();
        assertThat(req.attributes()).isEmpty();
        assertThat(req.overrides()).isEqualTo(RagOverrides.empty());
    }

    @Test
    void nullQueryIsRejected() {
        RagRequest.Builder builder = RagRequest.builder();
        assertThatThrownBy(builder::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("query");
    }

    @Test
    void builderCarriesAdHocAttributesForExtensibility() {
        RagRequest req = RagRequest.builder()
                .query("q")
                .attribute("tenant", "acme")
                .attribute("experiment", 42)
                .metadataFilter("lang", "ko")
                .streaming(true)
                .build();

        assertThat(req.attributes()).containsEntry("tenant", "acme").containsEntry("experiment", 42);
        assertThat(req.metadataFilters()).containsEntry("lang", "ko");
        assertThat(req.streaming()).isTrue();
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        java.util.Map<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("k", "v");

        RagRequest req = RagRequest.builder().query("q").attributes(mutable).build();
        mutable.put("k2", "v2");

        assertThat(req.attributes()).containsOnlyKeys("k");
    }
}
