# Agentic RAG Spring Starter — Module Plan & Design

> Spring AI 기반 Agentic RAG 기능을 제공하는 Spring Boot Starter 모듈군.
> 다른 서비스에서 의존성 추가만으로 탑재해 쓸 수 있는 표준 API를 제공한다.

---

## 1. 목표 (Goals)

- **Drop-in Starter**: 의존성 하나만 추가하면 Agentic RAG 전체 파이프라인이 자동 구성된다.
- **Pluggable Everywhere**: 파일 리더, 청킹 전략, 임베딩/LLM 프로바이더, 이벤트 발행기, 가드레일, 툴, 메모리 저장소 — 모두 교체 가능한 SPI로 제공.
- **Multi-Provider**: OpenAI / Anthropic / AWS Bedrock / Azure OpenAI / Ollama / vLLM을 동일 API로 사용.
- **Event-Driven**: ingestion · retrieval · agent · LLM 스트리밍 전 구간에서 외부 이벤트 발행 가능.
- **Agentic-native**: 6종 에이전트 오케스트레이션, 툴 콜링, MCP, self-correction, fact-checking을 1급 시민으로 지원.

## 2. Non-Goals

- 특정 벡터 DB에 종속되는 코드. (Spring AI `VectorStore` 추상화에 의존)
- UI / 프론트엔드. (모듈은 백엔드 라이브러리)
- 도메인 특화 로직(예: 법률). 일반 RAG 기능으로만 유지하고, 사용자 코드에서 확장한다.
- **Phase 1 범위에서는 Caching(semantic cache) 미포함** — Phase 2로 분리.

## 3. 기술 스택

| 항목 | 선택 | 비고 |
|---|---|---|
| Java | 21 LTS | records, sealed, virtual threads |
| Spring Boot | 3.4.x | auto-configuration AOT 호환 |
| Spring AI | 1.0.x GA | `ChatModel`, `EmbeddingModel`, `VectorStore`, `Advisor`, `ChatMemory`, `@Tool`, MCP |
| Gradle | 9.x (Kotlin DSL) | wrapper 동봉 |
| Build | Multi-module | starter + 세분 모듈 |
| Reactive | Project Reactor | streaming (`Flux<String>`) |
| Observability | Micrometer + OTel | 토큰/지연/cost 메트릭 |
| License | MIT | |

## 4. 모듈 구조

```
agentic-rag-spring-starter/                (root, no-jar)
├── agentic-rag-core                       # 공통 모델, SPI, 이벤트 타입, 예외
├── agentic-rag-ingestion                  # DocumentReader, ChunkingStrategy, Embedding 파이프라인
├── agentic-rag-retrieval                  # Vector/BM25/Hybrid 검색, Reranker
├── agentic-rag-agents                     # Agent Orchestrator, Tool 프로바이더, MCP 연동
├── agentic-rag-factcheck                  # Fact-checking, Validation Agent
├── agentic-rag-autoconfigure              # Spring Boot Auto-Configuration
└── agentic-rag-spring-boot-starter        # ⭐ 전체 의존 — 사용자가 주로 추가
```

- 패키지 루트: `kr.co.mz.agenticai.core`
- 모든 모듈은 `kr.co.mz.agenticai.core.<subdomain>` 하위에 패키지를 둔다.
  - `...core.common` (core 모듈)
  - `...core.ingestion` (ingestion)
  - `...core.retrieval`, `...core.agent`, `...core.factcheck`, `...core.autoconfigure`

### 4-1. 의존성 방향

```
starter  →  autoconfigure  →  {ingestion, retrieval, agents, factcheck}  →  core
```

- `core`는 다른 모듈에 의존하지 않는다.
- feature 모듈(`ingestion`/`retrieval`/`agents`/`factcheck`)은 서로 직접 참조하지 않는다. 필요 시 `core`의 SPI/이벤트를 통해 연결.
- `autoconfigure`만이 모든 feature 모듈을 묶어 Bean을 조립한다.
- `starter`는 `autoconfigure` + 모든 feature 모듈을 transitively 노출하는 shell (거의 빈 artifact).

### 4-2. 사용자 사용 패턴

```kotlin
// A. 풀세트 (대부분의 사용자)
implementation("kr.co.mz.agenticai:agentic-rag-spring-boot-starter:0.1.0")

// B. 골라쓰기 (ingestion 없이 retrieval만)
implementation("kr.co.mz.agenticai:agentic-rag-retrieval:0.1.0")
implementation("kr.co.mz.agenticai:agentic-rag-autoconfigure:0.1.0")
```

## 5. 핵심 공개 API

```java
// 진입점 — 질의 실행
public interface AgenticRagClient {
    RagResponse ask(RagRequest request);
    Flux<RagStreamEvent> askStream(RagRequest request);
}

// 문서 주입
public interface IngestionPipeline {
    IngestionResult ingest(IngestionRequest request);
    Flux<IngestionEvent> ingestAsync(IngestionRequest request);
}

// 확장 가능한 SPI
public interface DocumentReader {                 // 파일 형식 플러그인 (pdf/md/...)
    boolean supports(Resource resource);
    List<Document> read(Resource resource);
}
public interface ChunkingStrategy {               // 청킹 전략 (semantic/recursive/layout)
    List<Document> chunk(Document document, ChunkingContext ctx);
}
public interface RagEventPublisher {              // 이벤트 외부 발송 (Kafka/HTTP/...)
    void publish(RagEvent event);
}
public interface RetrieverRouter {                // 질의별 동적 라우팅
    List<Document> retrieve(RetrievalQuery query);
}
public interface Reranker { ... }
public interface FactChecker { ... }
public interface Guardrail { ... }                // 입력/출력 필터
public interface ToolProvider { ... }             // @Tool bean 수집
public interface MemoryStore { ... }              // Spring AI ChatMemory 어댑터
public interface AgentOrchestrator { ... }        // DAG/그래프 실행
```

## 6. 기능 상세

### 6-1. Ingestion (`agentic-rag-ingestion`)

**파이프라인**

```
DocumentReader  →  Cleaner  →  ChunkingStrategy  →  MetadataEnricher
                                                      →  EmbeddingModel  →  VectorStore (+ BM25 index, +선택적 Graph)
```

**ChunkingStrategy — 기본 제공**
- `FixedSizeChunkingStrategy`
- `RecursiveCharacterChunkingStrategy`
- `MarkdownHeadingChunkingStrategy` — `.md` 기본
- `LayoutAwarePdfChunkingStrategy` — `.pdf` 기본
- **`SemanticChunkingStrategy`** — 문장 단위로 나눈 뒤 인접 문장 임베딩 거리로 경계 탐지 (threshold/window/max-chunk 설정). 임베딩 호출 배치 최적화.

**DocumentReader — 기본 제공**
- `PdfDocumentReader` (Spring AI PDF reader 위에 wrapping)
- `MarkdownDocumentReader`

**Plugin 확장**
- 사용자가 `DocumentReader`/`ChunkingStrategy` Bean을 등록하면 자동 등록.
- `@Order` 또는 `supports(Resource)` 로 우선순위 판단.

**발행 이벤트**
- `DocumentReadEvent`, `DocumentChunkedEvent`, `ChunkEmbeddedEvent`, `IngestionCompletedEvent`, `IngestionFailedEvent`

### 6-2. Retrieval (`agentic-rag-retrieval`)

- **Vector 검색** — Spring AI `VectorStore` 위임.
- **BM25 검색** — Lucene 내장 BM25Searcher (또는 OpenSearch/Elasticsearch 어댑터 SPI).
- **Graph 검색** — 선택적, `KnowledgeGraphRetriever` SPI (Neo4j 어댑터는 Phase 2).
- **Hybrid Fusion** — RRF(Reciprocal Rank Fusion) 기본, Weighted-Sum 옵션.
- **Query Transformation**
  - HyDE, Multi-Query, Sub-question decomposition, Query Rewriting
  - `ChatModel` 의존 Bean(HyDE/Rewrite/MultiQuery)은 `@ConditionalOnBean(ChatModel.class)` 대신 **생성자 주입**에 의존한다. Spring AI의 `ChatModel` auto-config가 우리 조건 평가 후에 등록되는 순서 이슈를 피하기 위함이며, 사용자가 opt-in(`agentic-rag.retrieval.query.*.enabled=true`)했는데 `ChatModel`이 없으면 startup 단계에서 명시적으로 실패한다.
- **Reranker**
  - `NoopReranker` (기본), `CrossEncoderReranker` SPI (ONNX/사내 모델 어댑터 가능).
- **Routing**
  - `RetrieverRouter` — 의도/질문 성격에 따라 vector/bm25/graph 조합 선택.
- **발행 이벤트** — `QueryReceivedEvent`, `QueryTransformedEvent`, `RetrievalCompletedEvent`, `RerankCompletedEvent`.

### 6-3. Agents (`agentic-rag-agents`)

- **기본 에이전트 6종 템플릿**
  1. `IntentAnalysisAgent` — 질문 의도 분류, 라우팅 결정
  2. `RetrievalAgent` — 다단계 검색, 재질의
  3. `InterpretationAgent` — 검색 결과 해석/필터
  4. `DataConstructionAgent` — 컨텍스트 조립, summarize
  5. `SummaryAgent` — 최종 응답 작성
  6. `ValidationAgent` — fact-check 호출, 인용 검증
- **Orchestrator**
  - DAG/그래프 기반 실행, `AgentNode` → `Edge(condition)` → `AgentNode`.
  - LangGraph 스타일의 state machine, Max-iteration 제한, self-correction loop.
- **Tool Calling**
  - Spring AI `@Tool` 어노테이션 수집, `ToolProvider` SPI로 외부 Bean 노출.
  - **MCP** — `spring-ai-mcp-client` 연동, 외부 MCP 서버 등록 가능.
  - **A2A** (Agent-to-Agent) — Phase 2. 지금은 인터페이스만.
- **Memory**
  - Spring AI `ChatMemory` 위임. `InMemoryChatMemory` 기본, `JdbcChatMemory`/`RedisChatMemory` SPI.
- **Advisor Chain**
  - 로깅, PII 마스킹, prompt-injection 가드, rate-limit advisor 기본 제공.
- **발행 이벤트** — `AgentStartedEvent`, `AgentStepCompletedEvent`, `AgentFailedEvent`, `ToolInvokedEvent`.

### 6-4. Fact-Checking (`agentic-rag-factcheck`)

- `FactChecker` — 생성된 답변과 원문(근거 청크)을 실시간 대조.
- 전략: LLM-based (critic prompt), rule-based (숫자/날짜/인용 regex), embedding-similarity.
- 실패 시 orchestrator에 피드백 → **self-correction loop** 트리거.
- **Citation 강제** — 응답에 청크 ID·offset 자동 첨부 (`CitationAttacher`).
- 발행 이벤트 — `FactCheckPassedEvent`, `FactCheckFailedEvent`.

### 6-5. Events (`agentic-rag-core`)

- `RagEvent` (sealed interface) — 타입별 record.
- `ApplicationEventPublisher` 어댑터를 기본 `RagEventPublisher` 구현체로 제공.
- 사용자가 `RagEventPublisher` Bean을 등록하면 대체 (Kafka/Rabbit/HTTP/...).
- **스트리밍 이벤트** — `LlmTokenStreamedEvent`는 토큰마다 발행, non-blocking.

### 6-6. Model Providers

- Spring AI starter를 직접 쓰지 않고, **우리가 얇은 래퍼 auto-configuration**을 둬서 `application.yml`의 `agentic-rag.llm.provider=openai|anthropic|bedrock|azure|ollama|vllm` 만으로 스위치.
- vLLM은 OpenAI-compatible endpoint로 간주 (base-url override).
- **Model Routing 전략** — `ModelRouter` SPI (속도/품질/비용 태그 기반). Phase 1은 단일 provider + manual override, Phase 2에서 자동 라우팅.

### 6-7. Observability & Guardrails

- Micrometer `Timer`, `Counter` — 단계별 지연, 토큰 수, retrieval hit rate.
- OpenTelemetry 트레이싱 — agent step 별 span.
- Guardrail SPI — PII 마스킹, Prompt-injection 필터, Toxicity 필터.
- **Cost tracker** — provider 별 토큰 → 비용 환산, 이벤트로 노출.

### 6-8. Evaluation (`agentic-rag-core` 내부 서브 패키지)

- Golden dataset harness: JSON/YAML 포맷으로 질문·기대 답·기대 출처 정의.
- 메트릭: Recall@k, MRR, Faithfulness (LLM-as-judge), Answer Relevancy.
- 테스트 지원용 — 운영 의존성 아님.

## 7. Configuration (application.yml 예시)

```yaml
agentic-rag:
  llm:
    provider: openai                # openai | anthropic | bedrock | azure | ollama | vllm
    model: gpt-4o-mini
    base-url: ${LLM_BASE_URL:}
  embedding:
    provider: openai
    model: text-embedding-3-small
  ingestion:
    chunking:
      default-strategy: semantic
      semantic:
        threshold: 0.75
        max-chunk-tokens: 512
        window-size: 3
  retrieval:
    hybrid:
      enabled: true
      fusion: rrf                   # rrf | weighted
      weights: { vector: 0.6, bm25: 0.4 }
    rerank:
      enabled: false
  agents:
    orchestrator:
      max-iterations: 3
      enable-self-correction: true
  events:
    stream-tokens: true
  guardrails:
    pii-mask: true
    prompt-injection: true
```

## 8. 품질 기준

- Java 21, 모든 공개 API에 Javadoc.
- 공개 API는 `package-info.java` 로 경계 표시.
- 내부 전용 클래스는 `internal` 서브 패키지로 격리 (Java module-path 미사용; 컨벤션으로만).
- 테스트: JUnit 5, Testcontainers (Ollama / vector DB), Spring Boot auto-config 통합 테스트.
- 린트: Spotless + Checkstyle (Google Java Style 변형).
- CI: GitHub Actions workflow는 Phase 1 말미에 추가.

## 9. 빌드 · 퍼블리싱

- Gradle Kotlin DSL, 버전 카탈로그 (`gradle/libs.versions.toml`).
- `publishing` — Phase 1은 `mavenLocal()` 만. Phase 2에서 Nexus/Maven Central 구성.
- `javadocJar` / `sourcesJar` 기본 포함.

## 10. 디렉토리 / 레이아웃

```
agentic-rag-spring-starter/
├── build.gradle.kts                       # 루트 — plugin 관리, common config
├── settings.gradle.kts                    # 모듈 include
├── gradle.properties
├── gradle/libs.versions.toml              # 버전 카탈로그
├── gradle/wrapper/...
├── gradlew / gradlew.bat
├── LICENSE                                # MIT
├── README.md
├── .gitignore
├── .claudeignore
├── docs/
│   └── module-plan-and-design.md          # (이 문서)
├── agentic-rag-core/
│   ├── build.gradle.kts
│   └── src/main/java/kr/co/mz/agenticai/core/common/...
├── agentic-rag-ingestion/
│   └── src/main/java/kr/co/mz/agenticai/core/ingestion/...
├── agentic-rag-retrieval/
│   └── src/main/java/kr/co/mz/agenticai/core/retrieval/...
├── agentic-rag-agents/
│   └── src/main/java/kr/co/mz/agenticai/core/agent/...
├── agentic-rag-factcheck/
│   └── src/main/java/kr/co/mz/agenticai/core/factcheck/...
├── agentic-rag-autoconfigure/
│   └── src/main/java/kr/co/mz/agenticai/core/autoconfigure/...
└── agentic-rag-spring-boot-starter/
    └── (metadata only)
```

---

## 11. Phase 별 작업 계획

### Phase 0 — 프로젝트 초기화  *(이번 커밋)*
- [x] 모듈 구조 및 빌드 스캐폴딩 (7개 모듈)
- [x] Gradle Kotlin DSL + 버전 카탈로그
- [x] `.gitignore`, `.claudeignore`, `LICENSE(MIT)`, `README` 뼈대
- [x] 본 설계 문서 작성
- [x] Git 초기화

### Phase 1 — MVP (Core Agentic RAG)

**1-1. core 모델/SPI**
- [ ] `RagRequest/Response`, `RagStreamEvent`, `Document`, `Chunk`, `Citation` 모델
- [ ] `RagEvent` sealed + 타입별 record 정의
- [ ] 모든 SPI 인터페이스 골격
- [ ] `RagEventPublisher` — `ApplicationEventPublisher` 어댑터 기본 구현

**1-2. ingestion**
- [ ] `DocumentReader` — PDF, Markdown 기본 구현
- [ ] `ChunkingStrategy` — FixedSize, Recursive, MarkdownHeading, Semantic 구현
- [ ] `IngestionPipeline` + 단계별 이벤트 발행
- [ ] Embedding → VectorStore 적재

**1-3. retrieval**
- [ ] Vector / BM25 검색 구현 (BM25는 Lucene 내장)
- [ ] Hybrid fusion (RRF)
- [ ] Query transformation (HyDE, Multi-Query, Rewriting)
- [ ] Reranker SPI + Noop 기본 구현

**1-4. agents**
- [ ] Agent Orchestrator (DAG 실행기, max-iteration, self-correction loop)
- [ ] 6종 기본 Agent 템플릿
- [ ] Spring AI `@Tool` 수집 + `ToolProvider` SPI
- [ ] MCP client 통합
- [ ] `ChatMemory` 기본 in-memory

**1-5. factcheck**
- [ ] LLM-based FactChecker
- [ ] Citation attacher
- [ ] Self-correction 피드백 루프

**1-6. autoconfigure + starter**
- [ ] `@AutoConfiguration` 클래스들 (`@ConditionalOnMissingBean`, `@ConditionalOnProperty`)
- [ ] provider 스위칭 (openai/anthropic/bedrock/azure/ollama/vllm)
- [ ] `AgenticRagClient` / `IngestionPipeline` Bean 노출
- [ ] `application.yml` 스키마 (`@ConfigurationProperties`)
- [ ] starter 모듈 (메타데이터만)

**1-7. 공통**
- [ ] 스트리밍 API — `Flux<RagStreamEvent>`
- [ ] 기본 Advisor 3종 (logging, PII mask, prompt-injection)
- [ ] Micrometer 메트릭 기본
- [ ] Spring Boot 통합 테스트 (Testcontainers)
- [ ] 예제 서비스 (`examples/` 또는 별도 repo)

### Phase 2 — Advanced
- [ ] Semantic / Prompt Caching
- [ ] Corrective RAG (CRAG), Adaptive RAG 전략
- [ ] Graph Retrieval (Neo4j 어댑터)
- [ ] Cross-encoder Reranker 어댑터 (ONNX Runtime)
- [ ] A2A 프로토콜 구현
- [ ] Model Router (속도/품질/비용 자동 선택)
- [ ] Cost Tracker + Rate limiting advisor
- [ ] Multi-tenancy (namespace/권한 필터)
- [ ] Evaluation harness 완성 (LLM-as-judge)
- [ ] OpenTelemetry 트레이싱 고도화
- [ ] JDBC / Redis `MemoryStore` 구현체
- [ ] Kafka `RagEventPublisher` 레퍼런스 구현

### Phase 3 — 운영 성숙도
- [ ] Maven Central 퍼블리싱
- [ ] GitHub Actions CI/CD
- [ ] 문서 사이트 (MkDocs / Antora)
- [ ] 성능 벤치마크 (JMH)
- [ ] 보안 감사 (OWASP LLM Top 10 대응)

---

## 12. 오픈 이슈 / 결정 필요

- BM25 엔진: Lucene 내장 vs OpenSearch/Elasticsearch 의존. → **Phase 1은 Lucene 내장** 으로 시작, 이후 SPI 분리.
- Orchestrator DSL: Java fluent builder vs YAML/JSON 선언형. → **Phase 1: fluent builder**. YAML은 Phase 2 고려.
- vLLM: OpenAI-compatible로 단순 처리할지 전용 provider enum을 둘지. → `openai-compatible` enum으로 통합, `base-url`만 다르게.
- Graph retrieval은 Phase 1 미포함 (인터페이스만 노출).
