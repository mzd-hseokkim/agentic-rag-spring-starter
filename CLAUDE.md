# CLAUDE.md — agentic-rag-spring-starter

이 저장소에서 Claude가 지켜야 할 원칙. [Karpathy Guidelines](https://x.com/karpathy/status/2015883857489522876) 기반.

## 프로젝트

Spring AI 기반 Agentic RAG **Spring Boot Starter 모듈군**. 서비스가 아닌 라이브러리.

- 패키지 루트: `kr.co.mz.agenticai.core`
- Java 21, Spring Boot 3.4.x, Spring AI 1.0.x, Gradle 9.x Kotlin DSL (빌드만 Kotlin, 구현은 Java)
- 멀티모듈: `core`, `ingestion`, `retrieval`, `agents`, `factcheck`, `autoconfigure`, `spring-boot-starter`
- 상세 설계: [docs/module-plan-and-design.md](docs/module-plan-and-design.md)

---

## Karpathy Rules

### 1. Think Before Coding

- 가정은 명시한다. 불확실하면 **멈추고 질문한다**.
- 해석이 여러 개면 말없이 고르지 말고 선택지를 제시한다.
- 더 단순한 방법이 보이면 말한다. 필요하면 push back 한다.
- 헷갈리면 **무엇이** 헷갈리는지 이름을 붙여서 물어본다.

### 2. Simplicity First

- 요청된 것만. 추측성 기능·추상화·설정 플래그 금지.
- 단일 사용처 코드에 인터페이스 만들지 않는다. (SPI는 예외 — 이 프로젝트의 핵심 요구사항)
- 불가능한 시나리오에 대한 방어 코드 금지. 내부 코드는 신뢰한다.
- 200줄을 썼는데 50줄로 되면 다시 쓴다.
- 검증 기준: "시니어가 이거 과하다고 할까?" → Yes면 단순화.

### 3. Surgical Changes

- 요청된 파일/라인만 건드린다. 주변 코드 "김에 정리" 금지.
- 기존 스타일을 따른다. 내 취향대로 바꾸지 않는다.
- 무관한 dead code는 **삭제하지 말고 언급만** 한다.
- 내 변경이 만든 고아(사용 안 되는 import/변수)는 지운다. 그 이상은 지우지 않는다.
- 테스트: 바뀐 모든 라인이 사용자 요청으로 직접 추적되는가?

### 4. Goal-Driven Execution

- 성공 기준을 **작업 전에** 정한다.
  - "validation 추가" → "잘못된 입력에 대한 실패 테스트를 쓰고 통과시킨다"
  - "버그 수정" → "버그를 재현하는 테스트를 먼저 쓰고 통과시킨다"
  - "X 리팩토링" → "전후 테스트가 동일하게 통과한다"
- 다단계 작업은 짧은 계획을 먼저 진술: `[step] → verify: [check]`.
- "make it work" 같은 약한 기준은 금지.

---

## 프로젝트 규칙 (Karpathy rule이 적용될 구체적 지점)

### 모듈 경계
- `core`는 다른 내부 모듈에 의존하지 않는다.
- feature 모듈(`ingestion`/`retrieval`/`agents`/`factcheck`)은 서로 직접 참조 금지. `core`의 SPI/이벤트로만 연결.
- `autoconfigure`만 feature 모듈을 조립한다.
- `starter`는 metadata만. 로직 없음.

### 의존성 관리
- 모든 버전은 `gradle/libs.versions.toml` 단일 소스. 모듈 `build.gradle.kts`에서 버전 하드코딩 금지.

### SPI 설계
- 확장 지점은 `interface` + `@ConditionalOnMissingBean` 기본 구현.
- 사용자 Bean이 있으면 항상 override.
- 내부 전용 클래스는 `internal` 서브 패키지에.

### 이벤트
- ingestion / retrieval / agent / LLM streaming / factcheck 각 단계에서 이벤트 발행.
- `RagEventPublisher` SPI로 외부 발송 위임. 기본 구현은 `ApplicationEventPublisher` 어댑터.
- 스트리밍 토큰은 non-blocking.

### Phase 범위
- **Phase 1에서 캐싱 금지.** 필요해 보여도 지금 넣지 않는다. (Phase 2 항목)
- 그래프 검색, A2A, cost tracker, 다중 tenancy — 모두 Phase 2 이후.

### 주석
- 기본은 **주석 없음**. 식별자가 WHAT을 설명한다.
- 주석은 **비자명한 WHY만**: 숨겨진 제약, 특정 버그 우회, 놀라운 불변식.
- 이슈 번호/PR 번호/"added for X flow" 같은 문구 코드에 박지 않는다.
- 공개 API에만 Javadoc 필수.

### 파일 생성
- 새 `.md` 문서는 **사용자가 명시적으로 요청할 때만** 만든다.
- 기존 파일 편집 > 새 파일 생성.
- 임의 계획/분석 문서 생성 금지.

---

## 빌드

```bash
./gradlew build                        # 전 모듈 빌드
./gradlew :agentic-rag-core:test       # 개별 모듈 테스트
./gradlew publishToMavenLocal          # 로컬 퍼블리시 (Phase 1 유일)
```

테스트: JUnit 5 + AssertJ + Mockito. 통합 테스트는 Testcontainers.

---

## 커뮤니케이션

- 한국어 우선. 기술 용어는 영어 그대로.
- 파괴적/비가역 작업(force push, `rm -rf`, DB drop)은 실행 전 확인.
- 막히면 `--no-verify` 같은 shortcut으로 우회하지 말고 근본 원인을 찾는다.
