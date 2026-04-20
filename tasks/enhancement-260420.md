# SPI 아키텍처 개선 사항 (2026-04-20)

SPI 플러그인 아키텍처 검증 결과 발견된 개선 항목.

전체 구조는 설계 의도대로 잘 구현되어 있으며, 아래는 명시성/일관성 측면의 소소한 개선과 Phase 2 확장 후보를 정리한 것.

---

## P1 — 즉시 개선 가능 (기능 영향 없음, 명시성 향상)

### [x] 1. `AgenticRagGuardrailAutoConfiguration`에 클래스 레벨 `@ConditionalOnProperty` 추가

**현재 상태**
- 개별 Guardrail Bean에는 `@ConditionalOnProperty`가 있음
- 그러나 `AgenticRagGuardrailAutoConfiguration` 클래스 레벨에는 toggle 조건 없음
- 다른 AutoConfiguration(`ingestion`, `retrieval`, `agents`, `factcheck` 등)과 일관성 깨짐

**개선안**
```java
@AutoConfiguration(after = AgenticRagCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "agentic-rag.guardrails", name = "enabled", matchIfMissing = true)
public class AgenticRagGuardrailAutoConfiguration { ... }
```

**검증 기준**
- `agentic-rag.guardrails.enabled=false` 설정 시 모든 Guardrail Bean이 등록되지 않음을 확인하는 테스트 추가
- 기존 동작(기본 활성화)이 변경되지 않는지 확인

**영향도**: 낮음 (`matchIfMissing = true`로 기본 동작 유지)

---

### [x] 2. 쿼리 변환기 Bean의 `ChatModel` 의존 처리 방식 문서화

**현재 상태**
- `AgenticRagRetrievalAutoConfiguration`의 `hydeQueryTransformer`, `rewriteQueryTransformer`, `multiQueryTransformer`에 `@ConditionalOnBean(ChatModel.class)` 미사용
- 코드 주석으로 "Spring AI ChatModel 자동 구성이 우리 조건 평가 후에 등록될 수 있어 생성자 주입에 의존" 이라고 명시
- 의도된 설계지만 사용자가 ChatModel 미설정 시 startup 실패 메시지가 불친절할 수 있음

**개선안 (선택지)**
- (A) 현 상태 유지 + `docs/module-plan-and-design.md`에 명시적으로 문서화
- (B) `@ConditionalOnBean(ChatModel.class)` 추가 시도 + 통합 테스트로 회귀 검증
- (C) `ObjectProvider<ChatModel>`로 변경 후 부재 시 명확한 예외 throw

**권장**: (A) — 작동 중이고 주석에 근거 있음. 문서만 보강.

---

## P2 — Phase 2 SPI 확장 후보

설계 문서에 Phase 2로 명시된 SPI들. Extension point만 추가하고 에이전트/파이프라인 wiring은 범위 외.

- [x] **P2-1. `CrossEncoderReranker`** — `CrossEncoderScorer` SPI + reranker 구현. scorer 빈 존재 시 autoconfig가 `Reranker`로 등록 (미존재 시 `NoopReranker` 유지).
- [x] **P2-2. `MemoryStore`** — 대화 히스토리 추상화 (`append/history/clear`). `InMemoryMemoryStore` 기본 구현. 에이전트 wiring은 범위 외.
- [x] **P2-3. `ToolProvider`** — Spring AI `ToolCallback` 프로바이더. `EmptyToolProvider` 기본 구현. 에이전트 wiring은 범위 외.
- [ ] **P2-4. `ModelRouter`** — 다중 LLM 라우팅 (비용/지연/품질 기반). 별도 사이클.
- [ ] **P2-5. `KnowledgeGraphRetriever`** — Graph 검색 (Neo4j/JanusGraph 등). 별도 사이클.

---

## 검증 방법

P1 작업 완료 후:

```bash
./gradlew :agentic-rag-autoconfigure:test
```

특히 다음 시나리오를 통합 테스트로 추가:
1. `agentic-rag.guardrails.enabled=false` → Guardrail Bean 0개
2. `agentic-rag.guardrails.enabled` 미설정 → 기본 Guardrail Bean 등록됨
3. 사용자 정의 `Guardrail` Bean 등록 → 기본 구현이 override됨

---

## 참고

- 검증 기준 보고: 2026-04-20 SPI 아키텍처 검증 (대화 컨텍스트)
- 관련 문서: `docs/module-plan-and-design.md`
- 적용 원칙: CLAUDE.md의 Surgical Changes — 요청된 부분만 수정, "김에 정리" 금지
