import { useEffect, useState } from 'react';
import { getEnv } from '../api';
import type { ActuatorEnv } from '../api';

const TOGGLE_KEYS = [
  'agentic-rag.agents.enabled',
  'factcheck.enabled',
  'retrieval.query.hyde.enabled',
  'retrieval.query.multi-query.enabled',
] as const;

type ToggleKey = (typeof TOGGLE_KEYS)[number];

interface Settings {
  toggles: Record<ToggleKey, boolean>;
  activeProfiles: string[];
}

function flattenEnv(env: ActuatorEnv): Settings {
  const merged: Record<string, unknown> = {};
  for (const source of env.propertySources) {
    for (const [key, prop] of Object.entries(source.properties)) {
      if (!(key in merged)) {
        merged[key] = prop.value;
      }
    }
  }

  const toggles = {} as Record<ToggleKey, boolean>;
  for (const key of TOGGLE_KEYS) {
    const raw = merged[key];
    toggles[key] = raw === true || raw === 'true';
  }

  const profiles =
    env.activeProfiles.length > 0 ? env.activeProfiles : ['default'];

  return { toggles, activeProfiles: profiles };
}

export function SettingsPanel() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getEnv()
      .then((env) => setSettings(flattenEnv(env)))
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : String(err));
      });
  }, []);

  if (error) {
    return <section aria-label="Settings"><p>설정 로드 실패: {error}</p></section>;
  }

  if (!settings) {
    return <section aria-label="Settings"><p>설정 로딩 중…</p></section>;
  }

  return (
    <section aria-label="Settings">
      <h2>Settings</h2>

      <p>
        <strong>활성 프로파일:</strong>{' '}
        {settings.activeProfiles.join(', ')}
      </p>

      <ul>
        {TOGGLE_KEYS.map((key) => (
          <li key={key}>
            <label>
              <input
                type="checkbox"
                checked={settings.toggles[key]}
                disabled
                readOnly
              />
              {' '}{key}
            </label>
          </li>
        ))}
      </ul>
    </section>
  );
}
