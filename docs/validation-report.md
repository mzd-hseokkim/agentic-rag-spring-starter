# Demo 검증 리포트 (Validation Report)

> [validation-plan.md](validation-plan.md) 실행 결과를 채워 넣는 템플릿.
> **빈 항목은 다음 세션에서 실측 후 채움.**

- 실행 일시: `YYYY-MM-DD HH:MM KST`
- 실행자: `___`
- 환경: Windows 11 / Ollama `qwen3-embedding:4b` + `gpt-oss:20b` / 데모 앱 port 8080

---

## 요약

| 카테고리 | 합격 | 실패 | 보류 |
|---|---|---|---|
| 회귀 (1.1 ~ 1.5) | 5 | 0 | 0 |  <!-- 이전 세션에서 통과 -->
| 신규 (2.1 ~ 2.7) | 0 | 0 | 7 |
| **전체** | **5** | **0** | **7** |

---

## 1. 회귀 (이전 세션 결과, 재실행 생략 가능)

| # | 시나리오 | 응답 시간 | 상태 |
|---|---|---|---|
| 1.1 | 질의 grounded | 11.9s | ✅ |
| 1.2 | 질의 out-of-scope | 11.7s | ✅ |
| 1.3 | SSE 스트리밍 | 14.1s | ✅ 158 token + 1 completed |
| 1.4 | multipart 업로드 | 4.4s | ✅ 3 chunks |
| 1.5 | prompt-injection 차단 | 7ms | ✅ blocked |

---

## 2. 신규 시나리오

### 2.1 `POST /ingest/url` — 원격 URL ingest

| 항목 | 기대 | 실측 |
|---|---|---|
| HTTP status | 200 | `___` |
| `totalChunks` | > 5 | `___` |
| 응답 시간 | < 30s | `___` |
| 후속 `/ask` 질의 | 해당 문서 citation 포함 | `___` |

**에러/관찰 노트**:
```
___
```

---

### 2.2 PII 마스킹

#### 2.2.a 입력 마스킹

| 항목 | 기대 | 실측 |
|---|---|---|
| 응답 HTTP | 200 | `___` |
| 로그에서 쿼리가 `[REDACTED:PHONE]`/`[REDACTED:EMAIL]` 로 치환 | Yes | `___` |

로그 샘플:
```
___
```

#### 2.2.b 출력 마스킹

| 항목 | 기대 | 실측 |
|---|---|---|
| `pii-doc.md` 업로드 | totalChunks ≥ 1 | `___` |
| 전화번호 질의 답변 | `010-9876-5432` 미포함, `[REDACTED:PHONE]` 포함 | `___` |
| 주민번호 질의 답변 | `[REDACTED:RRN]` 포함 | `___` |

답변 샘플:
```
___
```

---

### 2.3 FactCheck 실패 경로

(전제: `factcheck.min-confidence: 0.9`)

| 항목 | 기대 | 실측 |
|---|---|---|
| 응답 HTTP | 200 | `___` |
| citations 개수 | 0 또는 적음 | `___` |
| `agentic.rag.factcheck.failed` 카운터 증가 | Yes | `___` |

답변 / attributes:
```json
___
```

---

### 2.4 Agent Orchestrator

(전제: `agents.enabled: true`)

#### 2.4.a factual 질의

| 항목 | 기대 | 실측 |
|---|---|---|
| `attributes.agentTrace` 존재 | Yes | `___` |
| trace 순서 | intent → retrieval → interpretation → data-construction → summary → validation | `___` |
| `attributes.iteration` | 1 (정상) 또는 2+ (self-correction 발동) | `___` |
| `agentic.rag.agent.run.iterations` 카운터 | ≥ 1 | `___` |

agentTrace 실측:
```
___
```

#### 2.4.b conversational 질의 ("안녕하세요")

| 항목 | 기대 | 실측 |
|---|---|---|
| `agentTrace` 에 `retrieval:skip` 또는 skip 표시 | Yes | `___` |
| 답변 | 인사 응답 (검색 안 함) | `___` |

---

### 2.5 HyDE / MultiQuery

(전제: `retrieval.query.hyde.enabled: true`, `multi-query.enabled: true, count: 3`)

| 항목 | 기대 | 실측 |
|---|---|---|
| 서버 로그에 HyDE / MultiQuery 호출 기록 | Yes | `___` |
| `agentic.rag.retrieval.hits` count 증가량 | 이전 세션의 ≥ 4배 (multi-query 확장) | `___` |
| 답변 품질 | 이전보다 구체적 또는 citation 풍부 | `___` |

비교 (쿼리 "Agentic RAG의 핵심 구성 요소"):
- Before (2.4 시점): answer 길이 `___`, citations `___`
- After HyDE: answer 길이 `___`, citations `___`

---

### 2.6 FactCheck 성공 메트릭

(전제: `factcheck.min-confidence: 0.5` 복원)

| 항목 | 기대 | 실측 |
|---|---|---|
| `agentic.rag.factcheck.passed` 카운터 | ≥ 1 | `___` |

---

### 2.7 종합 메트릭 스냅샷

모든 시나리오 실행 후:

```
___ (여기에 `curl /actuator/metrics | jq .names` 결과 붙여넣기)
```

**기대 목록** (8개+):
- `agentic.rag.ingestion.completed`
- `agentic.rag.ingestion.chunks`
- `agentic.rag.retrieval.duration`
- `agentic.rag.retrieval.hits`
- `agentic.rag.rerank.duration`
- `agentic.rag.llm.duration`
- `agentic.rag.factcheck.passed`
- `agentic.rag.factcheck.failed`
- `agentic.rag.agent.run.iterations` (agents 모드에서)

---

## 3. 발견 사항 / 버그 / 개선 아이디어

> 실측 중 발견된 이슈 기록.

- [ ] `___`
- [ ] `___`

---

## 4. 최종 결론

- [ ] Phase 1 MVP 범위 전원 검증 완료
- [ ] README 에 "검증 완료" 섹션 추가
- [ ] 발견된 버그 N개 → 별도 이슈로 분리
- [ ] Phase 2 후보 목록 업데이트
