import { useEffect, useState } from 'react';
import { getMetric } from '../api/actuator';
import type { ActuatorMetric } from '../api/actuator';

interface MetricState {
  value: number | null;
  count: number | null;
  stale: boolean;
  loading: boolean;
}

type MetricName = 'agentic.rag.llm.duration' | 'agentic.rag.retrieval.hits';

const METRIC_NAMES: MetricName[] = [
  'agentic.rag.llm.duration',
  'agentic.rag.retrieval.hits',
];

const METRIC_LABELS: Record<MetricName, string> = {
  'agentic.rag.llm.duration': 'LLM Duration',
  'agentic.rag.retrieval.hits': 'Retrieval Hits',
};

const POLL_INTERVAL_MS = 5000;

function extractMeasurement(metric: ActuatorMetric, statistic: string): number | null {
  const m = metric.measurements.find((x) => x.statistic === statistic);
  return m != null ? m.value : null;
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '—';
  if (ms >= 1000) return `${(ms / 1000).toFixed(2)} s`;
  return `${ms.toFixed(0)} ms`;
}

function formatHits(val: number | null): string {
  if (val == null) return '—';
  return String(Math.round(val));
}

export default function MetricsPanel() {
  const [states, setStates] = useState<Record<MetricName, MetricState>>({
    'agentic.rag.llm.duration': { value: null, count: null, stale: false, loading: true },
    'agentic.rag.retrieval.hits': { value: null, count: null, stale: false, loading: true },
  });

  useEffect(() => {
    let active = true;

    async function poll() {
      const results = await Promise.allSettled(
        METRIC_NAMES.map((name) => getMetric(name)),
      );

      if (!active) return;

      setStates((prev) => {
        const next = { ...prev } as Record<MetricName, MetricState>;
        METRIC_NAMES.forEach((name, i) => {
          const result = results[i];
          if (result.status === 'fulfilled') {
            const metric = result.value;
            const totalValue = extractMeasurement(metric, 'TOTAL_TIME') ?? extractMeasurement(metric, 'VALUE') ?? extractMeasurement(metric, 'COUNT');
            const countValue = extractMeasurement(metric, 'COUNT');
            next[name] = {
              value: totalValue,
              count: countValue,
              stale: false,
              loading: false,
            };
          } else {
            next[name] = {
              ...prev[name],
              stale: !prev[name].loading,
              loading: false,
            };
          }
        });
        return next;
      });
    }

    poll();
    const id = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      active = false;
      clearInterval(id);
    };
  }, []);

  return (
    <div className="metrics-panel">
      <h2>Metrics</h2>
      <div className="metrics-cards">
        {METRIC_NAMES.map((name) => {
          const s = states[name];
          const label = METRIC_LABELS[name];
          const isDuration = name === 'agentic.rag.llm.duration';

          let displayValue: string;
          if (s.loading) {
            displayValue = 'Loading…';
          } else if (isDuration) {
            displayValue = formatDuration(s.value);
          } else {
            displayValue = formatHits(s.value);
          }

          return (
            <div key={name} className={`metric-card${s.stale ? ' metric-card--stale' : ''}`}>
              <div className="metric-card__label">{label}</div>
              <div className="metric-card__value">{displayValue}</div>
              {isDuration && s.count != null && (
                <div className="metric-card__sub">count: {Math.round(s.count)}</div>
              )}
              {s.stale && <span className="metric-card__badge">stale</span>}
            </div>
          );
        })}
      </div>
    </div>
  );
}
