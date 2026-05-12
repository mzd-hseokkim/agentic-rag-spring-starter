import { useState } from 'react';
import { ingestFile, ingestUrl } from '../api/ingestion';
import type { IngestionResult } from '../api/ingestion';
import { Dropzone } from '../components/Dropzone';
import { ToastList } from '../components/ToastList';
import { useToast } from '../components/useToast';
import styles from './IngestPanel.module.css';

function resultMessage(r: IngestionResult): string {
  return `청크 ${r.totalChunks}개 인덱싱 완료 (${r.elapsedMillis}ms)`;
}

export function IngestPanel() {
  const [busy, setBusy] = useState(false);
  const [urlValue, setUrlValue] = useState('');
  const { toasts, show, dismiss } = useToast();

  async function handleFile(file: File) {
    if (busy) return;
    setBusy(true);
    try {
      const result = await ingestFile(file);
      show('success', `${file.name} — ${resultMessage(result)}`);
    } catch (err) {
      show('error', `파일 업로드 실패: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setBusy(false);
    }
  }

  async function handleUrlSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const url = urlValue.trim();
    if (!url) {
      show('error', 'URL을 입력하세요.');
      return;
    }
    if (busy) return;
    setBusy(true);
    try {
      const result = await ingestUrl(url);
      show('success', `URL 인덱싱 완료 — ${resultMessage(result)}`);
      setUrlValue('');
    } catch (err) {
      show('error', `URL 인덱싱 실패: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={styles.panel}>
      <h2 className={styles.title}>문서 인덱싱</h2>

      <div className={styles.section}>
        <h3 className={styles.subtitle}>파일 업로드</h3>
        <Dropzone onFile={handleFile} disabled={busy} />
      </div>

      <div className={styles.section}>
        <h3 className={styles.subtitle}>URL 인덱싱</h3>
        <form className={styles.urlForm} onSubmit={handleUrlSubmit}>
          <input
            className={styles.urlInput}
            type="url"
            placeholder="https://example.com/document.pdf"
            value={urlValue}
            onChange={(e) => setUrlValue(e.target.value)}
            disabled={busy}
            aria-label="인덱싱할 URL"
          />
          <button
            type="submit"
            className={styles.submitBtn}
            disabled={busy}
          >
            {busy ? '처리 중…' : '인덱싱'}
          </button>
        </form>
      </div>

      <ToastList toasts={toasts} onDismiss={dismiss} />
    </section>
  );
}
