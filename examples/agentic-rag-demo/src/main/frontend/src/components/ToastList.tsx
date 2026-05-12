import type { Toast } from './useToast';
import styles from './ToastList.module.css';

interface ToastListProps {
  toasts: Toast[];
  onDismiss: (id: number) => void;
}

export function ToastList({ toasts, onDismiss }: ToastListProps) {
  if (toasts.length === 0) return null;

  return (
    <div className={styles.list} role="status" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className={`${styles.toast} ${styles[t.kind]}`}>
          <span className={styles.message}>{t.message}</span>
          <button
            type="button"
            className={styles.close}
            onClick={() => onDismiss(t.id)}
            aria-label="닫기"
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
