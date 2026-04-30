# agentic-rag-demo

스타터의 end-to-end 동작을 실제로 띄워보는 Spring Boot 데모 애플리케이션.

## 사전 요건

- **Ollama** (http://localhost:11434) 실행 중
- 모델 pull:
  ```bash
  ollama pull qwen3-embedding:4b
  ollama pull gpt-oss:20b
  ```
- **Redis** (localhost:6379) — 대화 메모리 E2E 검증용. 가장 간단한 띄우기:
  ```bash
  docker run --rm -d --name agentic-rag-redis -p 6379:6379 redis:7-alpine
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

### 5. Phase 2 기능 E2E

`MemoryStore` 와 `ToolProvider` 는 `SummaryAgent` (오케스트레이션 모드)에서 사용되므로,
이 절의 시나리오는 `tools` 프로필(=`agents.enabled=true`)로 실행한다:

```bash
./gradlew :examples:agentic-rag-demo:bootRun -Pprofile=tools
```

#### Redis 기반 대화 메모리

`sessionId` 를 같은 값으로 두 번 호출하면 두 번째 호출이 첫 번째의 맥락을 이어받는다.
Redis에는 `agentic-rag-demo:memory:<sessionId>` 키가 LIST 형태로 쌓이고 1시간 TTL이 갱신된다.

```bash
# 1) 첫 질의 — 사용자 정보 등록
curl -sS -X POST http://localhost:8080/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"내 이름은 홍길동입니다.","sessionId":"demo-1"}' | jq .answer

# 2) 같은 세션으로 후속 질의 — 첫 메시지를 기억해야 함
curl -sS -X POST http://localhost:8080/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"방금 알려준 내 이름이 뭐였죠?","sessionId":"demo-1"}' | jq .answer

# 3) Redis 키 확인
docker exec -it agentic-rag-redis redis-cli KEYS 'agentic-rag-demo:memory:*'
docker exec -it agentic-rag-redis redis-cli LRANGE agentic-rag-demo:memory:demo-1 0 -1
docker exec -it agentic-rag-redis redis-cli TTL  agentic-rag-demo:memory:demo-1
```

기대 결과:
- (2) 응답에 "홍길동" 포함
- `KEYS` 가 `demo-1` 항목을 보여주고, `LRANGE` 결과는 USER/ASSISTANT 메시지 4건 (각 라운드 입출력 한 쌍)
- `TTL` ≈ 3600초 이하 (각 append 시 1h로 갱신됨)

#### `@Tool` 카탈로그 기반 tool calling

`DemoTools.currentTimeKst()` 가 `@Tool` 메서드로 노출되어 있고,
스타터의 `CatalogToolProvider` 가 자동 집계한다.

```bash
# 시간을 묻는 질의
curl -sS -X POST http://localhost:8080/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"지금 한국 시간 알려줘.","sessionId":"demo-tool"}' | jq .answer
```

기대 결과:
- 데모 로그에 `[tool] currentTimeKst() = 2026-04-30 15:23:11` 같은 라인이 출력
- 응답에 같은 시각 문자열이 포함

차단 동작 확인은 `application.yml` (또는 `application-tools.yml`) 에
`agentic-rag.tools.denied-names: [currentTimeKst]` 를 추가하고 재시작 — 같은 질의에서 도구가 호출되지 않는다.

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
