import { fetchJson, fetchMultipart } from './client';
import type { IngestionResult } from './types';

export function ingestFile(file: File): Promise<IngestionResult> {
  const form = new FormData();
  form.append('file', file, file.name);
  return fetchMultipart<IngestionResult>('/ingest', form);
}

export function ingestUrl(url: string): Promise<IngestionResult> {
  return fetchJson<IngestionResult>('/ingest/url', {
    method: 'POST',
    body: JSON.stringify({ url }),
  });
}

export type { IngestionResult };
