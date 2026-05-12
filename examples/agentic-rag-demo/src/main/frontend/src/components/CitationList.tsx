import type { Citation } from '../api/types';
import styles from './CitationList.module.css';

interface Props {
  citations: Citation[];
}

function shortId(documentId: string): string {
  return documentId.slice(0, 8);
}

function sourceName(citation: Citation): string {
  const src = citation.metadata['source'];
  if (typeof src === 'string' && src) {
    return src.split('/').pop() ?? src;
  }
  return shortId(citation.documentId);
}

export function CitationList({ citations }: Props) {
  if (citations.length === 0) {
    return <p className={styles.empty}>no sources</p>;
  }

  return (
    <ul className={styles.list} aria-label="citations">
      {citations.map((c, i) => (
        <li key={`${c.documentId}-${i}`} className={styles.chip} title={c.documentId}>
          <span className={styles.name}>{sourceName(c)}</span>
          <span className={styles.id}>{shortId(c.documentId)}</span>
        </li>
      ))}
    </ul>
  );
}
