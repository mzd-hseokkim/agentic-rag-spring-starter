# Agentic RAG 개요

## 정의

Agentic RAG는 Retrieval-Augmented Generation에 에이전트 기반 의사결정을 결합한 기법입니다. 전통적인 RAG가 단일 패스(검색 → 생성)로 동작하는 반면, Agentic RAG는 여러 전문 에이전트가 순차·반복적으로 협력해 답변을 만듭니다.

## 핵심 구성 요소

### Retriever

사용자 질의를 벡터 검색과 BM25 키워드 검색 양쪽에 보내 후보 청크를 얻습니다. 두 결과는 RRF(Reciprocal Rank Fusion) 알고리즘으로 융합되어 하이브리드 랭킹이 만들어집니다. 한국어 문서의 경우 Lucene Nori 형태소 분석기가 조사와 어미를 분리해 토큰화 품질을 높입니다.

### Query Transformer

사용자 질문을 그대로 검색하면 어휘 불일치가 자주 발생합니다. HyDE는 LLM이 만든 가상 답변을 벡터 쿼리로 사용하고, Multi-Query는 한 질문을 여러 표현으로 확장하며, Query Rewriting은 대명사와 구어체를 명시적 키워드로 바꿉니다.

### Agent Orchestrator

의도 분석, 검색, 해석, 데이터 구축, 요약, 검증 여섯 단계 에이전트가 순차적으로 실행됩니다. Validation 단계에서 답변이 출처로 뒷받침되지 않으면 Retrieval 단계부터 재실행하는 self-correction 루프가 돌아가며, 최대 반복 횟수 안에서 더 나은 답변을 시도합니다.

### Fact Checker

생성된 답변을 원본 청크와 실시간으로 대조해 환각을 걸러냅니다. LLM을 판정자로 활용해 "뒷받침 여부"와 "신뢰도 점수"를 JSON으로 받아 그대로 응답의 citation 필드에 첨부합니다.

## 장점

Agentic RAG의 가장 큰 장점은 정확성입니다. 단일 패스 RAG는 첫 검색이 빈약하면 그대로 환각을 출력하지만, self-correction 루프는 검증 실패 시 재검색을 시도하기 때문에 법률·의료처럼 정확성이 중요한 도메인에서 특히 유용합니다.
