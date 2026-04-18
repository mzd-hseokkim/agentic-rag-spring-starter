# agentic-rag-spring-starter

Spring AI 기반 **Agentic RAG** 기능을 한국어에 최적화해 제공하는 Spring Boot Starter 모듈군.
의존성 하나만 추가하면 벡터/BM25 하이브리드 검색, 멀티 에이전트 오케스트레이션, Fact-checking, Guardrails,
Micrometer 메트릭까지 전부 자동 구성된다.

## Status

**Phase 1 (MVP) 완료.** 125개 테스트 통과, Ollama 로컬 환경에서 12개 시나리오 end-to-end 실측 검증 완료.
Phase 2는 Tool calling / MCP / semantic cache / cross-encoder reranker 등 (하단 로드맵).

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
- **Observability** — Micrometer 메트릭 (MeterRegistry 존재 시 자동). OTel 트레이싱은
  `micrometer-tracing-bridge-otel` 추가 시 자동 연계

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

## Roadmap (Phase 2)

- Tool calling (Spring AI `@Tool`) + MCP client 통합
- Semantic / prompt caching
- Cross-encoder Reranker (ONNX Runtime)
- Graph retrieval (Neo4j 어댑터)
- Corrective RAG (CRAG) / Adaptive RAG
- `MemoryStore` JDBC / Redis 구현체
- Kafka `RagEventPublisher` 레퍼런스 구현
- Multi-tenancy (namespace / 권한 필터)
- Cost tracker + rate-limit guardrail
- Evaluation harness (Recall@k, MRR, Faithfulness)
- Maven Central 퍼블리싱

## License

[MIT](LICENSE)
