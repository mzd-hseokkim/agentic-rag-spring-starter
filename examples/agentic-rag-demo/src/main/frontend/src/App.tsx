import { useEffect, useRef, useState } from 'react'
import { ChatPanel } from './panels/ChatPanel'
import { IngestPanel } from './panels/IngestPanel'
import { SettingsPanel } from './panels/SettingsPanel'
import MetricsPanel from './panels/MetricsPanel'
import styles from './styles/layout.module.css'

type HealthStatus = 'unknown' | 'up' | 'down'

function useHealthPoll(intervalMs = 5000): HealthStatus {
  const [status, setStatus] = useState<HealthStatus>('unknown')
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    const check = () => {
      fetch('/actuator/health', { method: 'GET' })
        .then((r) => setStatus(r.ok ? 'up' : 'down'))
        .catch(() => setStatus('down'))
    }
    check()
    timerRef.current = setInterval(check, intervalMs)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [intervalMs])

  return status
}

function App() {
  const health = useHealthPoll(5000)

  return (
    <div className={styles.appShell}>
      <header className={styles.header}>
        <span
          className={styles.healthDot}
          data-status={health === 'unknown' ? undefined : health}
          title={`Backend: ${health}`}
        />
        <h1 className={styles.headerTitle}>Agentic RAG Playground</h1>
      </header>

      <div className={styles.grid}>
        <div className={styles.chatCol}>
          <ChatPanel />
        </div>

        <div className={styles.sideStack}>
          <IngestPanel />
          <SettingsPanel />
          <MetricsPanel />
        </div>
      </div>
    </div>
  )
}

export default App
