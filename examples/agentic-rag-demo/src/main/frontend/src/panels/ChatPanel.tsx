import { useEffect, useReducer, useRef, useState } from 'react';
import { ask, askStream } from '../api/rag';
import type { Citation, RagResponse } from '../api/types';
import { CitationList } from '../components/CitationList';
import { MessageList } from '../components/MessageList';
import styles from './ChatPanel.module.css';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  citations?: Citation[];
}

type Action =
  | { type: 'userSubmit'; id: string; text: string }
  | { type: 'assistantStart'; id: string }
  | { type: 'tokenAppend'; id: string; token: string }
  | { type: 'assistantDone'; id: string; response: RagResponse }
  | { type: 'error'; id: string; message: string };

function reducer(state: ChatMessage[], action: Action): ChatMessage[] {
  switch (action.type) {
    case 'userSubmit':
      return [...state, { id: action.id, role: 'user', text: action.text }];
    case 'assistantStart':
      return [...state, { id: action.id, role: 'assistant', text: '' }];
    case 'tokenAppend':
      return state.map((m) =>
        m.id === action.id ? { ...m, text: m.text + action.token } : m,
      );
    case 'assistantDone':
      return state.map((m) =>
        m.id === action.id
          ? { ...m, text: action.response.answer, citations: action.response.citations }
          : m,
      );
    case 'error':
      return state.map((m) =>
        m.id === action.id ? { ...m, text: `[오류] ${action.message}` } : m,
      );
    default:
      return state;
  }
}

let msgSeq = 0;
function nextId(prefix: string) {
  return `${prefix}-${++msgSeq}`;
}

export function ChatPanel() {
  const [messages, dispatch] = useReducer(reducer, []);
  const [input, setInput] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [streamMode, setStreamMode] = useState(true);
  const [busy, setBusy] = useState(false);
  const esCleanupRef = useRef<(() => void) | null>(null);

  function cleanup() {
    if (esCleanupRef.current) {
      esCleanupRef.current();
      esCleanupRef.current = null;
    }
  }

  useEffect(() => () => cleanup(), []);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const query = input.trim();
    if (!query || busy) return;

    cleanup();
    setInput('');
    setBusy(true);

    const userMsgId = nextId('u');
    dispatch({ type: 'userSubmit', id: userMsgId, text: query });

    const assistantMsgId = nextId('a');
    dispatch({ type: 'assistantStart', id: assistantMsgId });

    const sid = sessionId.trim() || undefined;

    if (streamMode) {
      const stop = askStream(
        query,
        (token) => dispatch({ type: 'tokenAppend', id: assistantMsgId, token }),
        (response) => {
          dispatch({ type: 'assistantDone', id: assistantMsgId, response });
          esCleanupRef.current = null;
          setBusy(false);
        },
        () => {
          dispatch({ type: 'error', id: assistantMsgId, message: '스트림 연결 오류' });
          esCleanupRef.current = null;
          setBusy(false);
        },
        sid,
      );
      esCleanupRef.current = stop;
    } else {
      try {
        const response = await ask(query, sid);
        dispatch({ type: 'assistantDone', id: assistantMsgId, response });
      } catch (err) {
        dispatch({
          type: 'error',
          id: assistantMsgId,
          message: err instanceof Error ? err.message : String(err),
        });
      } finally {
        setBusy(false);
      }
    }
  }

  const lastAssistant = [...messages].reverse().find((m) => m.role === 'assistant');
  const lastCitations = lastAssistant?.citations;

  return (
    <section className={styles.panel}>
      <div className={styles.header}>
        <h2 className={styles.title}>채팅</h2>
        <label className={styles.toggle}>
          <input
            type="checkbox"
            checked={streamMode}
            onChange={(e) => setStreamMode(e.target.checked)}
            disabled={busy}
          />
          Stream
        </label>
      </div>

      <MessageList messages={messages} />

      {lastCitations !== undefined && (
        <div className={styles.citations}>
          <span className={styles.citationsLabel}>Sources</span>
          <CitationList citations={lastCitations} />
        </div>
      )}

      <form className={styles.inputRow} onSubmit={handleSubmit}>
        <input
          className={styles.sessionInput}
          type="text"
          placeholder="Session ID (선택)"
          value={sessionId}
          onChange={(e) => setSessionId(e.target.value)}
          disabled={busy}
          aria-label="Session ID"
        />
        <input
          className={styles.queryInput}
          type="text"
          placeholder="질문을 입력하세요…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={busy}
          aria-label="질문 입력"
        />
        <button type="submit" className={styles.sendBtn} disabled={busy || !input.trim()}>
          {busy ? '…' : '전송'}
        </button>
      </form>
    </section>
  );
}
