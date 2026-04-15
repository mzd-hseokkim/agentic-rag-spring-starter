package kr.co.mz.agenticai.core.factcheck;

/** Korean-tuned prompts for LLM-as-judge fact-checking. */
public final class KoreanFactCheckPrompts {

    public static final String VERIFY = """
            당신은 답변 검증 전문가입니다.
            아래 [답변]이 [출처 문서]에 의해 사실로 뒷받침되는지 평가합니다.
            출처에 없는 사실을 답변이 단정하면 grounded=false 로 표시하세요.

            반드시 아래 JSON 한 객체만 출력합니다. 코드블록·설명·머리말 금지.

            {"grounded": true 또는 false,
             "confidence": 0.0 ~ 1.0 사이의 숫자,
             "reason": "한 문장으로 판단 근거",
             "supportingSourceIndexes": [출처 인덱스 0-based 정수 배열]}

            [질문]
            {query}

            [답변]
            {answer}

            [출처 문서]
            {sources}
            """;

    private KoreanFactCheckPrompts() {}
}
