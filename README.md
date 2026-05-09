# agentic-rag-spring-starter

Spring AI 기반 **Agentic RAG** 기능을 한국어에 최적화해 제공하는 Spring Boot Starter 모듈군.
의존성 하나만 추가하면 벡터/BM25 하이브리드 검색, 멀티 에이전트 오케스트레이션, Fact-checking, Guardrails,
Micrometer 메트릭까지 전부 자동 구성된다.

## Status

**Phase 1 (MVP) 완료.** Ollama 로컬 환경에서 12개 시나리오 end-to-end 실측 검증 완료.

**Phase 2 진행 중.**
- ✅ Tool calling — `@Tool` / MCP `ToolCallbackProvider` 빈을 자동 집계하는 `CatalogToolProvider`
- ✅ Conversational memory — JSON-LIST 기반 `RedisMemoryStore` + TTL
- ⏳ Cross-encoder reranker, semantic cache, graph retrieval, CRAG 등 (하단 로드맵)

## Features

- **Ingestion** — PDF / Markdown DocumentReader (플러그인 확장 가능), NFC 정규화, 4종 청킹 전략
  (FixedSize / Recursive / MarkdownHeading / **Semantic**)
- **한국어 특화** — Lucene **Nori** 형태소 분석기, 한국어 문장 경계 splitter, 한국어 프롬프트 기본
- **Hybrid Retrieval** — Vector (Spring AI `VectorStore`) + BM25 (in-memory Lucene) + **RRF** 융합,
  Weighted-sum fusion, Noop reranker (cross-encoder는 SPI로 교체 가능)
- **Query Transformation** — HyDE / Rewrite / Multi-Query (LLM 기반, opt-in)
- **Agentic Orchestration** — 6-agent 파이프라인
  (Intent → Retrieval → Interpretation → DataConstruction → Summary → Validation),
  validation 실패 시 self-correction loop
- **Fact-Checking** — LLM-as-judge, JSON 구조 응답, 환각 탐지 + citation 첨부
- **Guardrails** — PII 마스킹, Prompt-injection 방어, 요청/응답 로깅
- **Streaming** — `AgenticRagClient.askStream(...)` → `Flux<RagStreamEvent>`,
  토큰 단위 이벤트 발행
- **Eventing** — Ingestion / Retrieval / LLM / FactCheck / Agent 모든 단계에서 `RagEvent` 발행.
  기본 `ApplicationEventPublisher` 어댑터 + 사용자가 Kafka/HTTP 등 구현체 등록 가능
- **Conversational Memory** — `MemoryStore` SPI (raw 저장) + `MemoryPolicy` SPI (trimming).
  기본은 in-memory, classpath에 Spring Data Redis 존재 시 JSON-LIST 기반 Redis 구현체로 자동 교체
  (TTL · key prefix 설정). 정책 3종 (`RecentMessages` / `TokenWindow` / `RollingSummary`) 기본 제공,
  `application.yml`로 선택 가능
- **Tool Calling** — `ToolProvider` SPI. `@Tool` 빈 / MCP tool source 등 모든
  `ToolCallbackProvider`를 카탈로그로 집계, 이름 기반 allow/deny 필터 지원
- **Observability** — Micrometer 메트릭 (MeterRegistry 존재 시 자동). OTel 트레이싱은
  `micrometer-tracing-bridge-otel` 추가 시 자동 연계.
  query path 전 구간 (retrieval → agent planner/synthesizer → LLM call → factcheck) span 발행,
  `rag-correlation-id` baggage로 단일 query 내 분산 추적 가능

## Modules

| Module | Purpose |
|---|---|
| `agentic-rag-core` | 공통 모델, SPI, 이벤트, 예외, Guardrail 기본 구현 |
| `agentic-rag-ingestion` | DocumentReader, 청킹 전략, TextNormalizer, IngestionPipeline |
| `agentic-rag-retrieval` | Lucene BM25, Fusion, Reranker, QueryTransformers, HybridRetrieverRouter |
| `agentic-rag-agents` | 6 Agent, SequentialAgentOrchestrator, 기본/Orchestrator AgenticRagClient |
| `agentic-rag-factcheck` | LlmFactChecker, Korean prompts |
| `agentic-rag-autoconfigure` | Spring Boot Auto-Configuration, Observability listener |
| `agentic-rag-spring-boot-starter` | ⭐ 풀세트 starter — 사용자가 보통 이것만 의존 |

## Quick Start

**build.gradle.kts**

```kotlin
dependencies {
    implementation("kr.co.mz.agenticai:agentic-rag-spring-boot-starter:0.1.0-SNAPSHOT")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.boot:spring-boot-starter-actuator")  // 메트릭 원할 때
}
```

**application.yml**

```yaml
spring.ai.ollama:
  base-url: http://localhost:11434
  embedding.options.model: qwen3-embedding:4b
  chat.options.model: gpt-oss:20b

agentic-rag:
  language: ko                        # 한국어 기본 (Nori + 한국어 프롬프트)
  retrieval:
    overscan-factor: 3
    query:
      hyde:        { enabled: true }  # 선택
      multi-query: { enabled: true, count: 3 }
  factcheck:
    enabled: true                     # 환각 탐지
    min-confidence: 0.5
  agents:
    enabled: false                    # true 면 6-agent 오케스트레이션으로 전환
  memory:
    history-limit: 10                 # SummaryAgent에 prefix 되는 과거 메시지 수
    redis:
      enabled: true                   # Spring Data Redis 가 classpath에 있을 때 자동 활성
      key-prefix: "agentic-rag:memory:"
      ttl: 24h                        # 대화별 TTL (append 시 갱신)
  tools:
    enabled: true                     # ToolCallbackProvider 빈을 모아 자동 노출
    allowed-names: []                 # 비어있으면 전체 허용
    denied-names: []                  # 차단할 도구 이름
  guardrails:
    pii-mask:         { enabled: true }
    prompt-injection: { enabled: true }
```

**사용 코드**

```java
@Service
class MyService {
    private final IngestionPipeline ingestion;
    private final AgenticRagClient client;

    MyService(IngestionPipeline ingestion, AgenticRagClient client) {
        this.ingestion = ingestion;
        this.client = client;
    }

    void load(Resource md) {
        ingestion.ingest(IngestionRequest.of(md));   // 문서 주입
    }

    RagResponse ask(String q) {
        return client.ask(RagRequest.of(q));         // 동기 RAG
    }

    Flux<RagStreamEvent> stream(String q) {
        return client.askStream(RagRequest.of(q));   // 스트리밍
    }
}
```

## Memory Policy

`MemoryStore` (raw 저장)와 `MemoryPolicy` (trimming)은 분리된 SPI다.
`MemoryStore`는 이력을 append/load/clear하고, `MemoryPolicy`는 LLM에 전달하기 직전 history를 가공한다.

### 정책 선택 가이드

| 정책 | `memory.policy.type` | 특징 | 권장 상황 |
|------|---------------------|------|----------|
| `RecentMessagesPolicy` | `RECENT` | 최근 N개 메시지만 전달 | 짧은 Q&A, 저비용 모델 |
| `TokenWindowPolicy` | `TOKEN_WINDOW` | 토큰 budget 내 최신 메시지 최대 유지 | 중간 길이 대화, 토큰 비용 민감 |
| `RollingSummaryPolicy` | `ROLLING_SUMMARY` | budget 초과 시 오래된 부분을 LLM으로 요약해 SystemMessage에 압축 | 장기 대화, 맥락 손실 최소화 |

```yaml
agentic-rag:
  memory:
    policy:
      type: ROLLING_SUMMARY      # RECENT | TOKEN_WINDOW | ROLLING_SUMMARY
      window-size: 20            # RecentMessagesPolicy: 메시지 수
      token-budget: 4000         # TokenWindow / RollingSummary: 토큰 예산
      summarize-fraction: 0.5    # RollingSummary: 한 번에 압축할 비율 (0 < x < 1)
      recent-turns: 6            # RollingSummary: 항상 verbatim 유지할 최소 메시지 수
```

> `ROLLING_SUMMARY` 사용 시 `ChatModel` 빈이 반드시 등록되어 있어야 한다.

### JDBC MemoryStore 어댑터 작성 예제

JDBC 구현체는 starter에 포함되지 않는다. `MemoryStore` SPI를 직접 구현한 뒤 빈으로 등록하면
`@ConditionalOnMissingBean` 규칙에 따라 기본 구현체를 대체한다.

```java
@Component
public class JdbcMemoryStore implements MemoryStore {

    private final JdbcTemplate jdbc;

    public JdbcMemoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String conversationId, MemoryRecord record) {
        jdbc.update(
            "INSERT INTO memory_records (conversation_id, role, content, created_at) VALUES (?,?,?,?)",
            conversationId, record.role().name(), record.content(), record.timestamp()
        );
    }

    @Override
    public List<MemoryRecord> history(String conversationId, int limit) {
        return jdbc.query(
            "SELECT role, content, created_at FROM memory_records " +
            "WHERE conversation_id = ? ORDER BY created_at DESC LIMIT ?",
            (rs, i) -> new MemoryRecord(
                MemoryRecord.Role.valueOf(rs.getString("role")),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant()
            ),
            conversationId, limit
        );
    }

    @Override
    public void clear(String conversationId) {
        jdbc.update("DELETE FROM memory_records WHERE conversation_id = ?", conversationId);
    }
}
```

Spring AI `ChatMemory`와 함께 사용하려면 `SpringAiChatMemoryAdapter`를 통해 `ChatMemory`
인터페이스로 노출할 수 있다 (autoconfigure에서 `@ConditionalOnMissingBean(ChatMemory.class)`로 자동 등록).

## Observability

### OTel 트레이싱 활성화

기본 의존성만으로는 Micrometer 메트릭만 수집된다. OTel 분산 추적을 원하면 런타임 classpath에 다음을 추가한다.

**build.gradle.kts**

```kotlin
dependencies {
    // OTel bridge — runtime only (compileOnly로 이미 guard됨)
    runtimeOnly("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp")
}
```

**application.yml**

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # 개발: 전량, 운영: 0.1 권장
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

### Span 구조

query path 1회 실행 시 다음 span 트리가 생성된다.

```
rag.agent.run              (OrchestratorAgenticRagClient — 6-agent 모드 시)
  rag.agent.planner        (IntentAnalysisAgent — 의도 분류 LLM call)
  rag.retrieval.vector     (HybridRetrieverRouter — vector 검색)
  rag.retrieval.bm25       (HybridRetrieverRouter — BM25 검색)
  rag.retrieval.fusion     (RRF 융합)
  rag.retrieval.rerank     (재랭킹)
  rag.agent.synthesizer    (SummaryAgent — 답변 생성 LLM call)
  rag.factcheck            (LlmFactChecker — 선택적)
rag.llm.call               (DefaultAgenticRagClient — 단일-pass 모드 시 LLM call)
```

### Baggage 전파

`rag-correlation-id` 키로 단일 query 내 모든 span이 같은 correlation-id를 공유한다.
`DefaultAgenticRagClient`가 query 시작 시 ID를 생성해 baggage에 주입하고,
`HybridRetrieverRouter`는 baggage에서 읽어 재사용한다 (중복 생성 방지).

### Attribute 카디널리티

| Attribute | 카디널리티 | span |
|---|---|---|
| `rag.retriever_id` | low | retrieval |
| `rag.retrieval_k` | low | retrieval |
| `rag.fusion_strategy` | low | fusion |
| `rag.reranker_class` | low | rerank |
| `rag.llm.provider` | low | llm.call |
| `rag.llm.model_class` | low | llm.call |
| `rag.factcheck.verdict` | low | factcheck |
| `rag.correlation_id` | **high** | 전체 |
| `rag.retrieval_hits` | **high** | retrieval |
| `rag.factcheck.confidence` | **high** | factcheck |

UUID 기반 `rag.correlation_id`는 `highCardinalityKeyValue`로 등록되므로 OTel exporter의 메트릭 라벨로 노출되지 않는다.

## Architecture

```
┌───────────────┐      IngestionRequest
│ User Code     │────────┐
└───────────────┘        ▼
                   ┌─────────────────────────────────────┐
                   │ IngestionPipeline                   │
                   │  DocumentReader → NFC → Chunking    │
                   │   → fan-out to ChunkSinks           │
                   └────┬───────────────┬────────────────┘
                        ▼               ▼
                  VectorStore      LuceneBm25Index
                   (Spring AI)     (Nori analyzer)

┌───────────────┐      RagRequest
│ User Code     │────────┐
└───────────────┘        ▼
                   ┌─────────────────────────────────────┐
                   │ AgenticRagClient                    │
                   │  INPUT guardrails (PII / injection) │
                   │   → [Agent Orchestrator] (옵션)     │
                   │      intent → retrieval → ...       │
                   │      ↓ self-correction on fail      │
                   │   → RetrieverRouter                 │
                   │      (QueryTransform → [sources × N]│
                   │       → Fusion → Rerank)            │
                   │   → ChatModel (+ stream)            │
                   │   → FactChecker (옵션)              │
                   │   → OUTPUT guardrails               │
                   └──────────────┬──────────────────────┘
                                  ▼
                            RagResponse / Flux<RagStreamEvent>

                      모든 단계 → RagEvent → EventListener → Micrometer
```

## Extension Points (SPI)

핵심 인터페이스는 전부 `@ConditionalOnMissingBean` 기반 — 사용자 빈 등록 시 자동 override.

| SPI | 용도 |
|---|---|
| `DocumentReader` | 새 파일 형식 추가 (docx, html, ...) |
| `ChunkingStrategy` | 청킹 전략 교체 |
| `ChunkSink` | ingestion이 chunk를 보낼 새 목적지 (graph DB, audit log, ...) |
| `DocumentSource` | 검색 백엔드 추가 (graph retriever, 외부 검색엔진, ...) |
| `ResultFusion` | 융합 전략 |
| `Reranker` | 재랭킹 모델 (cross-encoder, LLM, ...) |
| `MemoryStore` | 대화 히스토리 저장 (in-memory / Redis 기본 제공, JDBC 등 추가 가능) |
| `ToolProvider` | LLM에게 노출할 도구 카탈로그 (Spring AI `@Tool` / MCP 자동 집계) |
| `Guardrail` | 입/출력 필터 |
| `RagEventPublisher` | 이벤트 외부 전달 (Kafka / HTTP / Slack) |
| `FactChecker` | 답변 검증 전략 |
| `Agent` | 오케스트레이터 파이프라인의 한 단계 |
| `AgentOrchestrator` | 전체 에이전트 실행 전략 |
| `RetrieverRouter` | 검색 라우팅 자체를 대체 |

## 한국어 지원

기본 설정 `language: ko` 시 자동 적용:
- **BM25**: `org.apache.lucene:lucene-analysis-nori` 형태소 분석기 — 조사/어미 분리 ("서울에서" → "서울")
- **Recursive chunking**: 한국어 문장 경계 separator (`"다. "`, `"요. "`, `"까? "`, `"습니다. "`)
- **Markdown heading chunking**: 언어 중립, 그대로 동작
- **NFC 정규화**: PDF의 자모 분해(NFD) Hangul을 자동 합성
- **프롬프트**: HyDE / Rewrite / MultiQuery / FactCheck / Summary 전부 한국어 기본

검증된 조합: **Ollama `qwen3-embedding:4b` (임베딩) + `gpt-oss:20b` (LLM)**.
OpenAI / Anthropic / Bedrock / Azure OpenAI / vLLM 도 Spring AI 스타터 추가만으로 전환 가능.

## Building

```bash
./gradlew build                        # 전 모듈 빌드 + 테스트
./gradlew :agentic-rag-core:test       # 개별 모듈
./gradlew publishToMavenLocal          # 로컬 퍼블리시
```

JDK 21 자동 provision (foojay resolver + Adoptium). Gradle 9.x Kotlin DSL.

## 검증 결과

Ollama `qwen3-embedding:4b` + `gpt-oss:20b` 환경에서 전 시나리오 실측 완료 (2026-04-19).
상세 결과는 [docs/validation-report.md](docs/validation-report.md) 참조.

| 시나리오 | 상태 | 핵심 확인 사항 |
|---|---|---|
| Grounded 질의 / Out-of-scope | ✅ | 정확한 답변 + citation, 범위 밖 질의 거절 |
| SSE 스트리밍 | ✅ | 158 토큰 + Completed 이벤트 |
| Multipart 파일 업로드 | ✅ | PDF/Markdown → 청킹 + 벡터/BM25 인덱싱 |
| URL ingest | ✅ | `POST /ingest/url` → 30 chunks (Spring AI README) |
| Prompt-injection 차단 | ✅ | 6ms 이내 즉시 차단, LLM 호출 없음 |
| PII 마스킹 (전화/주민번호) | ✅ | `[REDACTED:PHONE]`, `[REDACTED:RRN]` 치환 |
| FactCheck 메트릭 | ✅ | `factcheck.passed` 카운터 정상 등록 |
| Agent 6-step trace | ✅ | `intent→retrieval→interpretation→data-construction→summary→validation` |
| Agent self-correction skip | ✅ | `retrieval:skip` + `validation:skip` (대화형 질의) |
| Agent iteration 메트릭 | ✅ | `agent.run.iterations` 카운터 정상 등록 |
| 종합 Micrometer 메트릭 | ✅ | 8개 agentic 메트릭 등록 (ingestion·retrieval·rerank·llm·factcheck·agent) |

## Documentation

- [docs/module-plan-and-design.md](docs/module-plan-and-design.md) — Phase 1 설계 문서 (Phase 2 로드맵 포함)
- [docs/validation-plan.md](docs/validation-plan.md) — 검증 계획 (체크리스트)
- [docs/validation-report.md](docs/validation-report.md) — 검증 리포트 (실측 결과 + 발견 버그 + 수정 내역)
- [CLAUDE.md](CLAUDE.md) — 이 저장소에서의 코딩 원칙 (Karpathy Guidelines 기반)

## Roadmap

### Phase 2 — 고급 RAG 패턴

- ✅ Tool calling — `CatalogToolProvider` (Spring AI `@Tool` + MCP `ToolCallbackProvider` 집계)
- ✅ `MemoryStore` Redis 구현체 (`RedisMemoryStore`, JSON-LIST + TTL)
- Semantic / prompt caching
- Cross-encoder Reranker (ONNX Runtime)
- Graph retrieval (Neo4j 어댑터)
- Corrective RAG (CRAG) / Adaptive RAG
- `MemoryStore` JDBC 구현체
- Kafka `RagEventPublisher` 레퍼런스 구현
- Multi-tenancy (namespace / 권한 필터)
- Cost tracker + rate-limit guardrail
- Evaluation harness (Recall@k, MRR, Faithfulness)
- Maven Central 퍼블리싱

### Phase 3 — 프로덕션 품질

**멀티모달 처리**
- PDF 표/이미지 추출 `DocumentReader` — 표(table) → Markdown 변환, 이미지 캡션을 chunk에 포함
- 멀티모달 RAG — 이미지 기반 질의 + 텍스트 결합 검색 (Spring AI Vision 연계)
- DOCX / HTML / PPTX `DocumentReader` 확장

**응답 품질**
- 구조화된 출력 — JSON Schema 기반 응답 포맷 강제 (`@StructuredOutput`)
- Citation 소스 매핑 — 답변 내 인용 구간을 원본 문서의 정확한 위치(offset)와 매핑
- 피드백 SPI — 👍/👎 이벤트를 `RagEvent`로 발행, 서비스가 수집·활용할 수 있는 확장 지점

**안정성**
- LLM 폴백 — 1차 모델 장애 시 자동으로 대체 모델 전환 (circuit breaker 패턴)
- 프롬프트 외부화 — 프롬프트 템플릿을 `Resource`로 분리, 재배포 없이 교체 가능
- Guardrail 체이닝 — 여러 Guardrail의 실행 순서·단락(short-circuit) 전략 설정

**개발자 경험**
- Actuator Health Indicator — 벡터 DB 연결, LLM 모델 상태, BM25 인덱스 크기 자동 노출
- 테스트 픽스처 — `MockAgenticRagClient`, `StubFactChecker` 등 단위 테스트용 fake 구현체
- Starter BOM — 버전 충돌 방지를 위한 `agentic-rag-bom` artifact 제공

## License

[MIT](LICENSE)
