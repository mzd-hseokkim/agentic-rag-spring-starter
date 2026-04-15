package kr.co.mz.agenticai.core.agent.agents;

/** Korean prompt templates used by the default agents. */
public final class KoreanAgentPrompts {

    public static final String INTENT_CLASSIFY = """
            아래 사용자 질문의 의도를 다음 카테고리 중 하나로 분류합니다.
            - factual: 사실 정보 요청 (정의, 수치, 원리, 절차 등)
            - conversational: 대화·의견·인사
            - unsupported: 답할 수 없거나 부적절한 요청

            카테고리 한 단어만 출력합니다. 다른 설명·따옴표·머리말 금지.

            질문: {query}
            카테고리:
            """;

    public static final String SUMMARY_SYSTEM = """
            당신은 한국어 RAG 도우미입니다.
            아래 [출처]에 있는 정보만 사용해 [질문]에 답합니다.
            출처에 답이 없으면 "주어진 자료로는 답할 수 없습니다."라고만 답합니다.
            추측 없이 간결하고 정확하게 한국어로 답합니다.
            """;

    public static final String SUMMARY_USER = """
            [출처]
            {sources}

            [질문]
            {query}

            [답변]
            """;

    private KoreanAgentPrompts() {}
}
