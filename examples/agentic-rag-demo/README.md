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

## 프론트엔드 (Vite + React)

`src/main/frontend/` 에 React + TypeScript 앱이 있다. Gradle이 빌드 시 자동으로
`npm install` + `npm run build` 를 실행해 산출물을 `src/main/resources/static/` 에
배치하므로, **`bootRun` 하나로 백엔드 + UI가 같이 뜬다** (http://localhost:8080).

개발 중에는 HMR을 위해 Vite dev server를 별도로 띄우는 것이 편하다:

```bash
# Gradle 태스크로 (node 설치 불요 — 플러그인이 자동 설치)
./gradlew :examples:agentic-rag-demo:frontendDev

# 또는 직접 npm 으로
cd examples/agentic-rag-demo/src/main/frontend
npm install
npm run dev          # http://localhost:5173, /ask·/ingest·/actuator 는 8080으로 프록시
```

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

## 데모 시나리오

> 스타일링: CSS Modules (vanilla). Tailwind 미채택 — 단일 화면 4 패널에 과투자.

아래 순서로 4개 패널을 모두 사용해 보는 end-to-end 시연 가이드.

### 0. 기동

```bash
./gradlew :examples:agentic-rag-demo:bootRun
```

> 빌드 캐시 stale 문제 발생 시:
> ```bash
> ./gradlew :examples:agentic-rag-demo:clean bootRun
> ```

기동 완료 후 http://localhost:8080/ 접속 → 화면 좌측에 ChatPanel, 우측에 IngestPanel / SettingsPanel / MetricsPanel이 세로 스택으로 표시된다. 헤더의 초록 점이 백엔드 헬스를 표시한다.

### 1. 파일 Ingest (IngestPanel)

1. 우측 상단 **IngestPanel**에서 **파일 선택** 또는 드래그앤드롭으로 `.md` / `.pdf` 파일 업로드.
2. "Ingest" 버튼 클릭 → 진행 상태 확인.
3. 업로드 완료 toast 메시지 확인.

### 2. 질의 (ChatPanel)

1. 좌측 **ChatPanel** 입력창에 질문 입력 (예: *"업로드한 문서의 핵심 내용을 요약해줘"*).
2. **Stream / Sync 토글**로 응답 방식 전환 가능.
   - Stream: 토큰 단위로 실시간 출력.
   - Sync: 전체 응답을 한 번에 수신.
3. 응답 하단 **Citations** 섹션에서 참조 문서 확인.

### 3. 멀티턴 대화 (ChatPanel)

1. 세션 입력창에 임의 `sessionId` (예: `demo-1`) 입력.
2. 첫 질의: *"내 이름은 홍길동입니다."*
3. 후속 질의: *"방금 알려준 내 이름이 뭐였죠?"* → 응답에 "홍길동" 포함 여부 확인.

### 4. 설정 토글 확인 (SettingsPanel)

1. 우측 **SettingsPanel**에서 `agentic-rag.agents.enabled` / `factcheck.enabled` / `hyde.enabled` / `multi-query.enabled` 토글.
2. 토글 후 **ChatPanel**에서 같은 질의 재실행 → 응답 품질/구조 변화 확인.

### 5. 파일시스템 도구 시연 (tools 프로필)

`tools` 프로필에서 `agentic-rag.tools.fs.enabled=true` 가 활성화되어 `docs/` 디렉토리가 샌드박스로 마운트된다.

```bash
./gradlew :examples:agentic-rag-demo:bootRun -Pprofile=tools
```

**fs_listDir — 파일 목록 조회:**
```bash
curl -sS -X POST http://localhost:8080/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"docs 폴더에 어떤 파일들이 있어?","sessionId":"demo-fs"}' | jq .answer
```
기대 결과: `fs_listDir` 도구가 호출되어 `docs/` 하위 파일 목록이 응답에 포함된다.

**fs_readFile — 문서 내용 읽기:**
```bash
curl -sS -X POST http://localhost:8080/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"module-plan-and-design.md 파일의 핵심 내용을 요약해줘","sessionId":"demo-fs"}' | jq .answer
```

**fs_glob — 패턴으로 파일 찾기:**
```bash
curl -sS -X POST http://localhost:8080/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"docs 폴더에서 .md 파일을 모두 찾아줘","sessionId":"demo-fs"}' | jq .answer
```

**샌드박스 경계 확인 — `../` 탈출 차단:**
```bash
curl -sS -X POST http://localhost:8080/ask \
  -H 'Content-Type: application/json' \
  -d '{"query":"../src/main/java 폴더 파일 목록 보여줘","sessionId":"demo-fs"}' | jq .answer
```
기대 결과: 오류 응답("허용된 경로 밖입니다" 등) — 상위 디렉토리 탈출이 차단됨.

### 검증 기준

- `bootRun` 단일 명령 → http://localhost:8080/ 접속 시 4 패널 모두 렌더.
- ≥1024px 화면: ChatPanel 좌, 나머지 3개 패널 우측 세로 스택.
- <1024px 화면: 단일 컬럼으로 순차 표시 (모바일 본격 반응형은 범위 외).
