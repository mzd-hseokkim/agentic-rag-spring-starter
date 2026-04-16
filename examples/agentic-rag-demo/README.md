# agentic-rag-demo

스타터의 end-to-end 동작을 실제로 띄워보는 Spring Boot 데모 애플리케이션.

## 사전 요건

- **Ollama** (http://localhost:11434) 실행 중
- 모델 pull:
  ```bash
  ollama pull qwen3-embedding:4b
  ollama pull gpt-oss:20b
  ```
- JDK 21 (Gradle이 toolchain으로 자동 다운로드하므로 시스템 설치 불요)

## 실행

```bash
# 리포지토리 루트에서
./gradlew :examples:agentic-rag-demo:bootRun
```

기동 시 `src/main/resources/samples/*.md` 가 자동으로 ingest 된다.
로그에서 `Ingested 'agentic-rag-overview.md' → N chunks` 확인.

## API

### 1. 질의 (sync)
```bash
curl -sS -X POST http://localhost:8080/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"RRF 알고리즘은 어떤 역할을 하나요?"}' | jq
```

응답 예시:
```json
{
  "answer": "RRF는 벡터 검색과 BM25 결과를 융합...",
  "citations": [{"documentId":"...","metadata":{"source":"agentic-rag-overview.md"}}],
  "usage": {"promptTokens":0,"completionTokens":0,"totalTokens":0},
  "attributes": {}
}
```

### 2. 질의 (streaming / SSE)
```bash
curl -sS -N "http://localhost:8080/ask/stream?query=Agentic%20RAG란%20무엇인가요"
```

`TokenChunk` 이벤트가 토큰 단위로 흘러나오고 마지막에 `Completed` 이벤트.

### 3. 런타임 ingest

**파일 업로드 (multipart)** — 주된 사용 경로
```bash
curl -X POST http://localhost:8080/ingest \
  -F "file=@/path/to/doc.md"
```

업로드된 파일명이 보존되므로 `DocumentReader.supports()` 가 확장자로 리더를 고른다 (`.md`, `.pdf` 등).

**원격 URL 인제스트**
```bash
curl -X POST http://localhost:8080/ingest/url \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/doc.md"}'
```

### 4. 메트릭 (Actuator)
```bash
curl http://localhost:8080/actuator/metrics/agentic.rag.llm.duration
curl http://localhost:8080/actuator/metrics/agentic.rag.retrieval.hits
```

## 설정 변경

`src/main/resources/application.yml` 편집:

| 항목 | 기본값 | 효과 |
|---|---|---|
| `agentic-rag.agents.enabled` | `false` | `true` 로 바꾸면 6-agent 오케스트레이션 모드 (self-correction 포함) |
| `agentic-rag.factcheck.enabled` | `true` | LLM 검증 on/off |
| `agentic-rag.retrieval.query.hyde.enabled` | `false` | HyDE 쿼리 확장 on/off |
| `agentic-rag.retrieval.query.multi-query.enabled` | `false` | Multi-Query 확장 on/off |
| `spring.ai.ollama.chat.options.model` | `gpt-oss:20b` | LLM 모델 교체 |
| `spring.ai.ollama.embedding.options.model` | `qwen3-embedding:4b` | 임베딩 모델 교체 |

다른 vector store (pgvector / chroma / qdrant / ...) 를 쓰려면
`DemoConfig.vectorStore()` 빈을 제거하고 해당 Spring AI starter의존을 추가하면 된다.
