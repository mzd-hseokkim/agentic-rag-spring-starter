import { fetchJson } from './client';
import type { RagResponse, RagStreamEvent } from './types';

export function ask(query: string, sessionId?: string): Promise<RagResponse> {
  return fetchJson<RagResponse>('/ask', {
    method: 'POST',
    body: JSON.stringify({ query, sessionId: sessionId ?? null }),
  });
}

/**
 * Open an SSE stream for a RAG query.
 *
 * EventSource only supports GET and cannot add custom headers — acceptable
 * for the current unauthenticated demo. If authentication is required later,
 * replace with a polyfill or POST-based SSE client.
 *
 * @returns cleanup function — call on component unmount to close the stream.
 */
export function askStream(
  query: string,
  onToken: (text: string) => void,
  onComplete: (response: RagResponse) => void,
  onError: (err: Event) => void,
  sessionId?: string,
): () => void {
  const params = new URLSearchParams({ query });
  if (sessionId) params.set('sessionId', sessionId);
  const url = `/ask/stream?${params.toString()}`;
  const es = new EventSource(url);

  // Backend emits unnamed SSE events (default `message`) with two payload shapes:
  //   { text: string }                  → token chunk
  //   { response: RagResponse }         → final response (stream ends after)
  let completed = false;
  es.onmessage = (e: MessageEvent) => {
    const payload = JSON.parse(e.data) as { text?: string; response?: RagResponse };
    if (payload.text !== undefined) {
      onToken(payload.text);
    } else if (payload.response) {
      completed = true;
      onComplete(payload.response);
      es.close();
    }
  };

  es.onerror = (e: Event) => {
    // Server closes the stream after the final event; ignore that benign close.
    if (completed) return;
    onError(e);
    es.close();
  };

  return () => es.close();
}

// Re-export event types for convenience.
export type { RagResponse, RagStreamEvent };
