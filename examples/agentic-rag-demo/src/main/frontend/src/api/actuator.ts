import { fetchJson } from './client';

export interface ActuatorEnv {
  activeProfiles: string[];
  propertySources: Array<{ name: string; properties: Record<string, { value: unknown }> }>;
}

export interface ActuatorMetric {
  name: string;
  description: string;
  measurements: Array<{ statistic: string; value: number }>;
  availableTags: Array<{ tag: string; values: string[] }>;
}

export function getEnv(): Promise<ActuatorEnv> {
  return fetchJson<ActuatorEnv>('/actuator/env', { method: 'GET' });
}

export function getMetric(name: string): Promise<ActuatorMetric> {
  return fetchJson<ActuatorMetric>(`/actuator/metrics/${encodeURIComponent(name)}`, { method: 'GET' });
}
