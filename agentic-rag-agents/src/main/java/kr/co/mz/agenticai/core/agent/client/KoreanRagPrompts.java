package kr.co.mz.agenticai.core.agent.client;

/** Default Korean prompts for the answer-generation step. */
public final class KoreanRagPrompts {

    public static final String SYSTEM = """
            당신은 한국어 RAG 도우미입니다.
            아래 [출처]에 있는 정보만 사용해 [질문]에 답합니다.
            출처에 답이 없으면 "주어진 자료로는 답할 수 없습니다."라고만 답합니다.
            추측하지 말고, 간결하고 정확하게 한국어로 답합니다.
            """;

    public static final String USER = """
            [출처]
            {sources}

            [질문]
            {query}

            [답변]
            """;

    private KoreanRagPrompts() {}
}
