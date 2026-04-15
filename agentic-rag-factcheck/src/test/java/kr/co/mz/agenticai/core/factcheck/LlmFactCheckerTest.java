package kr.co.mz.agenticai.core.factcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.exception.AgenticRagException;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

class LlmFactCheckerTest {

    private static ChatModel mockReplying(String text) {
        ChatModel m = mock(ChatModel.class);
        when(m.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return m;
    }

    private FactChecker.FactCheckRequest req(String answer, Document... sources) {
        return new FactChecker.FactCheckRequest(answer, List.of(sources), "원본 질문", Map.of());
    }

    @Test
    void parsesGroundedJsonResponse() {
        var checker = new LlmFactChecker(mockReplying(
                "{\"grounded\":true,\"confidence\":0.9,\"reason\":\"근거 충분\",\"supportingSourceIndexes\":[0]}"));

        var result = checker.check(req("답변", new Document("c1", "근거 텍스트", Map.of())));

        assertThat(result.grounded()).isTrue();
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.reason()).isEqualTo("근거 충분");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).documentId()).isEqualTo("c1");
    }

    @Test
    void belowMinConfidenceMarksUngrounded() {
        var checker = new LlmFactChecker(mockReplying(
                "{\"grounded\":true,\"confidence\":0.3,\"reason\":\"약함\",\"supportingSourceIndexes\":[]}"),
                KoreanFactCheckPrompts.VERIFY, 0.5);

        var result = checker.check(req("답변", new Document("c1", "x", Map.of())));

        assertThat(result.grounded()).isFalse();
        assertThat(result.attributes()).containsEntry("rawVerdictGrounded", true);
    }

    @Test
    void extractsJsonFromSurroundingText() {
        // Some models prefix with "답변:" or similar. We must still find the JSON.
        var checker = new LlmFactChecker(mockReplying(
                "판단 결과는 다음과 같습니다.\n{\"grounded\":false,\"confidence\":0.2,\"reason\":\"근거 없음\",\"supportingSourceIndexes\":[]}\n\n끝."));

        var result = checker.check(req("답변", new Document("c1", "x", Map.of())));

        assertThat(result.grounded()).isFalse();
        assertThat(result.reason()).isEqualTo("근거 없음");
    }

    @Test
    void emptySourcesShortCircuits() {
        var checker = new LlmFactChecker(mock(ChatModel.class));

        var result = checker.check(new FactChecker.FactCheckRequest("답변", List.of(), null, Map.of()));

        assertThat(result.grounded()).isFalse();
        assertThat(result.confidence()).isZero();
    }

    @Test
    void invalidJsonThrows() {
        var checker = new LlmFactChecker(mockReplying("그냥 평문 응답입니다 JSON 없음"));

        assertThatThrownBy(() -> checker.check(req("답변", new Document("c1", "x", Map.of()))))
                .isInstanceOf(AgenticRagException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void invalidIndexesAreIgnored() {
        var checker = new LlmFactChecker(mockReplying(
                "{\"grounded\":true,\"confidence\":0.9,\"reason\":\"ok\",\"supportingSourceIndexes\":[0,99,-1]}"));

        var result = checker.check(req("답변",
                new Document("c1", "a", Map.of()),
                new Document("c2", "b", Map.of())));

        assertThat(result.citations()).extracting(c -> c.metadata().get("chunkId"))
                .containsExactly("c1");
    }

    @Test
    void rejectsBadMinConfidence() {
        assertThatThrownBy(() -> new LlmFactChecker(mock(ChatModel.class), KoreanFactCheckPrompts.VERIFY, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LlmFactChecker(mock(ChatModel.class), KoreanFactCheckPrompts.VERIFY, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
