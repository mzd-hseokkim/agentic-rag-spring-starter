// Mirrors kr.co.mz.agenticai.core.common.RagUsage
export interface RagUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

// Mirrors kr.co.mz.agenticai.core.common.Citation
export interface Citation {
  documentId: string;
  chunkIndex: number | null;
  charStart: number | null;
  charEnd: number | null;
  score: number | null;
  metadata: Record<string, unknown>;
}

// Mirrors kr.co.mz.agenticai.core.common.RagResponse
export interface RagResponse {
  answer: string;
  citations: Citation[];
  usage: RagUsage;
  attributes: Record<string, unknown>;
}

// Mirrors kr.co.mz.agenticai.core.common.IngestionResult
export interface IngestionResult {
  sourceDocumentIds: string[];
  totalChunks: number;
  chunkIds: string[];
  elapsedMillis: number;
  attributes: Record<string, unknown>;
}

// Mirrors RagStreamEvent sealed variants (SSE event names match record simple names)
export type TokenChunkEvent = { type: 'TokenChunk'; text: string };
export type CitationEmittedEvent = { type: 'CitationEmitted'; citation: Citation };
export type CompletedEvent = { type: 'Completed'; response: RagResponse };
export type FailedEvent = { type: 'Failed'; message: string };

export type RagStreamEvent =
  | TokenChunkEvent
  | CitationEmittedEvent
  | CompletedEvent
  | FailedEvent;
