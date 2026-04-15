package kr.co.mz.agenticai.core.retrieval.query;

/** Korean-tuned prompt templates for pre-retrieval query transformations. */
public final class KoreanQueryPrompts {

    /** Hypothetical Document Embeddings — LLM drafts a plausible answer used as the vector query. */
    public static final String HYDE = """
            다음 질문에 답할 수 있는 짧은 가상의 문서를 한국어로 작성해 주세요.
            사실 여부는 중요하지 않으며, 실제 참고 문서가 담고 있을 법한 문장 3~5개로 작성합니다.
            설명이나 머리말 없이 본문만 출력합니다.

            질문: {query}

            가상 문서:
            """;

    /** Query rewriting — produces a more specific, keyword-rich reformulation. */
    public static final String REWRITE = """
            아래 사용자 질문을 벡터 검색에 더 적합하도록 구체적이고 명확하게 다시 써 주세요.
            원래 의도를 유지하되, 모호한 대명사와 구어체 표현을 명시적 키워드로 바꾸세요.
            재작성된 질문 한 문장만 출력하며, 따옴표·설명·머리말은 넣지 않습니다.

            질문: {query}
            """;

    /** Multi-query expansion — generates N reformulations to widen recall. */
    public static final String MULTI_QUERY = """
            아래 원본 질문을 서로 다른 관점과 표현으로 재구성한 {number} 개의 대체 질문을 생성합니다.
            각 질문은 같은 정보를 찾지만 다른 검색 경로로 이어질 수 있어야 합니다.
            한 줄에 하나씩, 번호·따옴표·설명 없이 순수 질문 문장만 출력합니다.

            원본 질문: {query}
            """;

    private KoreanQueryPrompts() {}
}
