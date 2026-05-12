# Requirements: RAG Playground UI (E2E 화면 구성)

- **Slug**: rag-playground-ui
- **Mode**: default
- **Generated At**: 2026-05-12T07:19:11Z
- **Source**: jira-task-discover

## Stakeholders

- starter 개발자 자신 — E2E 검증 용도. ingest → ask → stream 흐름을 빠르게 돌려보고 동작/회귀를 눈으로 확인
- 외부 이해관계자 (데모데이) — 고객/PM/외부 청중에게 starter의 동작을 시연

## Goals & Success Criteria

- 엔드포인트 커버리지 · 0/4 → 4/4 · UI에서 `/ask`, `/ask/stream`, `/ingest`, `/ingest/url` 모두 호출 가능 (수동 시연)
- SSE 토큰 표시 지연 · N/A → <100ms · 토큰 도착 후 화면 반영까지의 체감 지연 (개발자 수동 확인)
- 멀티턴 컨텍스트 유지 · N/A → "방금 알려준 이름" 시나리오 통과 · 같은 sessionId 2회 호출 시 1회차 정보 회상
- 설정 가시성 · 0개 → 4개 토글 (agents/hyde/multi-query/factcheck) · `/actuator/env` 기반 read-only 표시
- 운영 가시성 · 0개 → ≥2개 메트릭 카드 (LLM duration, retrieval hits) · `/actuator/metrics` 폴링 표시

## Constraints

### Technical
- React 18 + Vite 5 + TypeScript (현재 `examples/agentic-rag-demo/src/main/frontend/` 스캐폴드 유지)
- `examples/agentic-rag-demo` 모듈 내부에 임베드 — Vite 빌드 산출물을 `src/main/resources/static/` 으로 출력해 Spring Boot가 그대로 서빙. `./gradlew :examples:agentic-rag-demo:bootRun` 한 명령어로 백엔드+UI 동시 기동
- 무거운 컴포넌트 키트(shadcn/ui, MUI 등) 도입 지양. 스타일링 라이브러리 자유 (vanilla CSS / CSS Modules / Tailwind 중 1택 — 구현자 재량)
- 백엔드는 WebFlux(WebFlux multipart), SSE는 `GET /ask/stream` (EventSource 호환)

### Schedule
- N/A — 해당 없음 (내부 데모용, 마일스톤 없음)

### Cost
- N/A — 해당 없음 (로컬 demo)

### Regulatory
- N/A — 해당 없음 (인증/감사 없음, 로컬 전용)

## Non-functional Requirements

| 항목 | 값 | 비고 |
|---|---|---|
| 성능 (응답시간/처리량) | SSE 토큰 화면 반영 지연 ≤100ms · 입력→첫 토큰 표시 ≤2s | 백엔드 LLM 지연은 제외 |
| 가용성 / SLA | N/A — 로컬 demo, SLA 없음 | |
| 보안 (인증/암호화) | N/A — 인증 비포함이 명시적 비목표 | |
| 확장성 (사용자/데이터 규모) | N/A — 단일 사용자 demo | |
| 관측성 (로깅/메트릭) | Actuator metrics/env 패널 노출 | FR-5, FR-6 |
| 호환성 (브라우저/OS/API) | 최신 Chromium 1개 (Chrome/Edge) 동작이면 충분 | EventSource 표준 API 사용 |

## Codebase Context

- `examples/agentic-rag-demo/src/main/java/kr/co/mz/agenticai/demo/RagController.java:43-75` — 4개 엔드포인트 (`POST /ingest` multipart, `POST /ingest/url`, `POST /ask`, `GET /ask/stream` SSE). `AskBody(query, sessionId)`, `UrlBody(url)` 레코드. 응답 타입: `IngestionResult`, `RagResponse(answer, citations, usage, attributes)`, `Flux<RagStreamEvent>`.
- `examples/agentic-rag-demo/src/main/resources/application.yml` — `agentic-rag.agents.enabled`, `agentic-rag.factcheck.enabled`, `agentic-rag.retrieval.query.hyde.enabled`, `agentic-rag.retrieval.query.multi-query.enabled` 토글.
- `examples/agentic-rag-demo/src/main/frontend/` — Vite + React + TS 스캐폴드 (App.tsx는 기본 카운터 화면 그대로). `vite.config.ts`에 `outDir: ../resources/static` 및 `/ask`·`/ingest`·`/actuator` 8080 프록시 설정 완료.
- `examples/agentic-rag-demo/build.gradle.kts` — `com.github.node-gradle.node` 플러그인으로 `frontendBuild` 태스크가 `processResources` 의존, `frontendDev` 태스크 (Vite dev server 기동).
- `examples/agentic-rag-demo/README.md` — Phase 2 시나리오(sessionId 멀티턴 / `@Tool` 카탈로그)와 Actuator 메트릭 이름.

## Functional Requirements

1. **FR-1 Chat 패널**: 입력창 + 메시지 히스토리(user/assistant) + sessionId 입력(옵션) + Send 버튼. 응답에 citations 인라인 표시(source 파일명, documentId 단축). *(source: Q2, code: examples/agentic-rag-demo/src/main/java/kr/co/mz/agenticai/demo/RagController.java:60-75)*
2. **FR-2 SSE 스트리밍 모드**: `EventSource`로 `/ask/stream?query=...` 구독 → `TokenChunk` 누적 표시(타이핑 효과), `Completed` 이벤트 수신 시 citations 최종 렌더 및 EventSource close. *(synthesized, code: examples/agentic-rag-demo/src/main/java/kr/co/mz/agenticai/demo/RagController.java:71-75)*
3. **FR-3 동기 Ask 모드 (토글)**: Chat 패널에 "Stream / Sync" 토글, Sync 시 `POST /ask`로 한 번에 받기. *(code: examples/agentic-rag-demo/src/main/java/kr/co/mz/agenticai/demo/RagController.java:60-69)*
4. **FR-4 Ingest 패널**: 파일 drag&drop (multipart `POST /ingest`) + URL 입력 폼 (`POST /ingest/url`). 결과(`IngestionResult` chunks 수) 토스트/배지 표시. *(code: examples/agentic-rag-demo/src/main/java/kr/co/mz/agenticai/demo/RagController.java:43-58)*
5. **FR-5 Settings/Profile 패널**: `GET /actuator/env`에서 `agentic-rag.agents.enabled`·`factcheck.enabled`·`retrieval.query.hyde.enabled`·`retrieval.query.multi-query.enabled` 값과 활성 프로파일을 읽어와 read-only 토글로 표시. *(synthesized)*
6. **FR-6 Metrics 패널**: `GET /actuator/metrics/agentic.rag.llm.duration`, `…retrieval.hits` 등 주요 메트릭을 카드로 표시. 폴링 주기는 Open Questions 참고. *(synthesized)*

## Goals ↔ FR 매핑

| Goal | 만족하는 FR | 비고 |
|---|---|---|
| 4개 엔드포인트 호출 (4/4) | FR-1, FR-3, FR-4 | Chat(sync/stream) + Ingest(file/url) |
| SSE 토큰 표시 지연 ≤100ms | FR-2 | EventSource 기반 |
| 멀티턴 sessionId 시나리오 통과 | FR-1 | sessionId 입력 + 동일 값 재사용 |
| 설정 가시성 (4 토글) | FR-5 | Actuator env |
| 운영 가시성 (≥2 메트릭) | FR-6 | Actuator metrics |

## Edge Cases

- 백엔드 다운/네트워크 오류 시 에러 표시 + 재시도 버튼 (재로딩 없이 회복) *(synthesized)*
- sessionId 없이 ask → 무세션 단발 호출로 동작, UI에서 "no session" 배지 *(synthesized)*
- SSE 도중 페이지 이탈/언마운트 → `EventSource.close()`로 leak 방지 *(synthesized)*
- citations 비어 있는 응답 → "no sources" 표시, 빈 영역 잔존 금지 *(synthesized)*
- 큰 파일 업로드 진행 중 UI 블로킹 금지 — 진행 indicator 표시 *(synthesized)*
- WebFlux backpressure로 SSE 이벤트가 묶여 도달 → 큐잉 후 순서대로 렌더 *(synthesized)*

## Out of Scope

- 로그인/인증/관리자 화면 *(source: Q-topic)*
- 멀티 테넌시 / 다중 사용자 격리 *(synthesized)*
- 채팅 내역 영구 저장 (백엔드 Redis가 책임) *(synthesized)*
- i18n (한국어/영어 단일) *(synthesized)*
- 다크 모드 (필요 시 후속) *(synthesized)*
- Settings 패널에서 토글 변경 write *(synthesized)*

## Open Questions

- [P2] Metrics 패널 폴링 주기 — 5s 권장하나 확정 필요 *(synthesized)*
- [P2] Settings 패널이 토글 write까지 지원할지 (현재 read-only 가정) *(synthesized)*
- [P3] 스타일링 라이브러리 최종 선택 (vanilla CSS vs CSS Modules vs Tailwind) — 구현자 재량 *(source: Q3)*

## Proposed Issue Breakdown

- **Story**: RAG Playground UI 구축 — Chat / Ingest / Settings / Metrics 4 패널을 가진 단일 페이지 SPA를 `examples/agentic-rag-demo` 내부에 임베드한다.
  - Sub-task 1: API 클라이언트 모듈 (`src/api/`) — `ask`, `askStream(EventSource)`, `ingestFile(FormData)`, `ingestUrl`, `getEnv`, `getMetric` 헬퍼
  - Sub-task 2: ChatPanel — 메시지 리스트 + 입력창 + sessionId 입력 + Stream/Sync 토글 + Citations 렌더 (blocks: 1)
  - Sub-task 3: IngestPanel — Dropzone + URL 폼 + 결과 토스트 (blocks: 1)
  - Sub-task 4: SettingsPanel — Actuator env에서 4 토글 + 활성 프로파일 read-only 표시 (blocks: 1)
  - Sub-task 5: MetricsPanel — Actuator metrics 폴링, 카드 표시 (LLM duration, retrieval hits 등) (blocks: 1)
  - Sub-task 6: 레이아웃/스타일 통합 + 샘플 시연 시나리오 README 보강 + 프로덕션 빌드 검증 (`./gradlew :examples:agentic-rag-demo:bootRun`으로 같은 origin 서빙 확인) (blocks: 2, 3, 4, 5)

### 실행 웨이브 (병렬 가능 단위)

> 표기 규칙: `(blocks: N)`은 "이 서브태스크가 sibling Sub-task N에 의해 blocked"를 의미한다 (요구사항 문서 컨벤션은 의존하는 쪽에 표기). Jira 링크 방향: Sub-task 1 → blocks → Sub-task 2~5; Sub-task 2~5 → blocks → Sub-task 6.

- **Wave 1 (단독)**: Sub-task 1 — 다른 모든 패널의 prerequisite
- **Wave 2 (Wave 1 완료 후, 최대 4명 병렬)**: Sub-task 2, 3, 4, 5 — `src/api/` 헬퍼를 import하므로 Sub-task 1 완료 필수
- **Wave 3 (Wave 2 모두 완료 후)**: Sub-task 6 — 4 패널을 단일 화면으로 통합 + README/빌드 검증

## Technical Approach Hint

### 핵심 구현 포인트
- `examples/agentic-rag-demo/src/main/frontend/src/` 아래에 `api/`, `panels/`, `components/`, `App.tsx`, `main.tsx`만 두고 단일 페이지로 구성. 글로벌 스토어(Redux/Zustand) 도입 없이 패널별 로컬 상태 + props로 충분.
- SSE는 `EventSource` 표준 API 사용. `addEventListener('TokenChunk', …)`, `addEventListener('Completed', …)`로 이름 있는 이벤트 처리. 언마운트 시 `close()`로 cleanup.
- 메시지 상태는 `useReducer` 권장 — token append가 빈번해 함수형 setState보다 의도가 명확하고 race-free.
- Actuator는 `/actuator/env`, `/actuator/metrics/{name}`. 같은 origin(prod) / Vite proxy(dev) 둘 다 CORS 문제 없음. 기본 노출이 제한적이면 `management.endpoints.web.exposure.include` 설정 추가 검토 (이미 application.yml에 있는지 확인 필요).

### 검토할 접근 옵션
- 옵션 A — **EventSource + 패널별 로컬 상태 (권장)**. 장점: 표준 API 자동 재연결, 의존 0. 단점: GET 전용 → 헤더 인증이 필요해지면 fetch+ReadableStream 마이그레이션 필요(현재는 무관).
- 옵션 B — fetch + ReadableStream으로 SSE 직접 파싱. 장점: POST·헤더 지원. 단점: 재연결/이벤트 파싱 직접 구현, 현 요구에 과함.
- 옵션 C — 스타일링: vanilla CSS Modules. 장점: 의존 0, 빌드 단순. 대안 Tailwind는 단일 화면 4 패널엔 과투자.

### 주의 지점
- `IngestionResult`/`RagResponse`/`RagStreamEvent`의 정확한 JSON 스키마를 starter 코어 모듈 클래스에서 1차 확인 후 TypeScript 타입 작성 (`agentic-rag-core` 참조). 추측 타입 금지 — 필드 누락 시 런타임 에러 위험.
- Vite dev 모드에서는 5173에서 서빙되지만 `EventSource('/ask/stream')`는 dev proxy를 통해 정상 동작해야 함 — `vite.config.ts` 프록시에 `ws: false`(기본) 유지 확인.
- Spring Boot가 `/static`을 서빙하면서 SPA fallback(존재하지 않는 경로 → `index.html`)이 필요해질 수 있음. 현재 단일 페이지라 라우터 없음 가정 — 라우터 도입 시에만 처리.
- `frontendBuild` 태스크가 `processResources`에 걸려 있어 `bootRun` 전에 자동 빌드. CI/로컬에서 node-gradle 플러그인이 node 자동 다운로드하므로 추가 설정 불요.
- Actuator metrics 엔드포인트 노출 설정이 `application.yml`에 없으면 추가 필요 (`management.endpoints.web.exposure.include: env,metrics`).
