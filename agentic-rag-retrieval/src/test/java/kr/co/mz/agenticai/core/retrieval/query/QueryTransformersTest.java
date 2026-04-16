package kr.co.mz.agenticai.core.retrieval.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.mz.agenticai.core.common.exception.RetrievalException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.Query;

class QueryTransformersTest {

    private static ChatResponse respondWith(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private ChatModel mockReplying(String text) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(respondWith(text));
        return chatModel;
    }

    // --- HyDE -------------------------------------------------------------

    @Test
    void hydeReplacesQueryWithHypotheticalAnswer() {
        var transformer = new HydeQueryTransformer(mockReplying("가상 답변 문서입니다."));

        Query out = transformer.transform(new Query("RAG는 뭐야?"));

        assertThat(out.text()).isEqualTo("가상 답변 문서입니다.");
    }

    @Test
    void hydeFallsBackToOriginalOnBlankResponse() {
        var transformer = new HydeQueryTransformer(mockReplying("   "));

        Query in = new Query("RAG는 뭐야?");
        assertThat(transformer.transform(in).text()).isEqualTo(in.text());
    }

    @Test
    void hydePropagatesFailuresAsRetrievalException() {
        ChatModel failing = mock(ChatModel.class);
        when(failing.call(any(Prompt.class))).thenThrow(new RuntimeException("timeout"));
        var transformer = new HydeQueryTransformer(failing);

        Query query = new Query("hi");
        assertThatThrownBy(() -> transformer.transform(query))
                .isInstanceOf(RetrievalException.class);
    }

    @Test
    void hydeRejectsTemplateMissingQueryPlaceholder() {
        assertThatThrownBy(() -> new HydeQueryTransformer(mock(ChatModel.class), "no placeholder"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Rewrite ----------------------------------------------------------

    @Test
    void rewriteReplacesQueryTextWithRewrittenVersion() {
        var transformer = new RewriteQueryTransformer(mockReplying("Spring Boot 자동 설정 원리"));

        Query out = transformer.transform(new Query("그거 어떻게 돼?"));

        assertThat(out.text()).isEqualTo("Spring Boot 자동 설정 원리");
    }

    // --- MultiQuery -------------------------------------------------------

    @Test
    void multiQueryParsesLineDelimitedOutput() {
        String raw = "Spring Boot 자동 설정 흐름\nSpring Boot 자동 구성 메커니즘\n자동 구성 후보 클래스 처리";
        var expander = new MultiQueryExpander(
                mockReplying(raw), KoreanQueryPrompts.MULTI_QUERY, 3, true);

        List<Query> out = expander.expand(new Query("자동 설정이란?"));

        // original + 3 variants
        assertThat(out).hasSize(4);
        assertThat(out.get(0).text()).isEqualTo("자동 설정이란?");
        assertThat(out.stream().skip(1).map(Query::text).toList())
                .containsExactly(
                        "Spring Boot 자동 설정 흐름",
                        "Spring Boot 자동 구성 메커니즘",
                        "자동 구성 후보 클래스 처리");
    }

    @Test
    void multiQueryStripsBulletPrefixes() {
        String raw = "1. 첫 번째 질문\n- 두 번째 질문\n• 세 번째 질문";
        var expander = new MultiQueryExpander(
                mockReplying(raw), KoreanQueryPrompts.MULTI_QUERY, 3, false);

        List<Query> out = expander.expand(new Query("원본"));

        assertThat(out).extracting(Query::text).containsExactly(
                "첫 번째 질문", "두 번째 질문", "세 번째 질문");
    }

    @Test
    void multiQueryRespectsCountLimit() {
        String raw = "a\nb\nc\nd\ne";
        var expander = new MultiQueryExpander(
                mockReplying(raw), KoreanQueryPrompts.MULTI_QUERY, 2, false);

        List<Query> out = expander.expand(new Query("원본"));

        assertThat(out).hasSize(2);
    }

    @Test
    void multiQueryRejectsInvalidParams() {
        assertThatThrownBy(() -> new MultiQueryExpander(
                mock(ChatModel.class), KoreanQueryPrompts.MULTI_QUERY, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MultiQueryExpander(
                mock(ChatModel.class), "no placeholders", 3, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
