import type { ChatMessage } from '../panels/ChatPanel';
import styles from './MessageList.module.css';

interface Props {
  messages: ChatMessage[];
}

export function MessageList({ messages }: Props) {
  return (
    <div className={styles.list} role="log" aria-live="polite">
      {messages.map((msg) => (
        <div key={msg.id} className={`${styles.bubble} ${styles[msg.role]}`}>
          <span className={styles.role}>{msg.role === 'user' ? '나' : 'AI'}</span>
          <p className={styles.text}>{msg.text}</p>
        </div>
      ))}
    </div>
  );
}
