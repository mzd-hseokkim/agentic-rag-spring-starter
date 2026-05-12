// Base URL is intentionally relative: dev uses Vite proxy, prod uses same origin.
const BASE = '';

export async function fetchJson<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  });
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`${res.status} ${res.statusText}: ${text}`);
  }
  return res.json() as Promise<T>;
}

export async function fetchMultipart<T>(
  path: string,
  body: FormData,
): Promise<T> {
  // Content-Type is intentionally omitted so the browser sets the boundary.
  const res = await fetch(`${BASE}${path}`, { method: 'POST', body });
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`${res.status} ${res.statusText}: ${text}`);
  }
  return res.json() as Promise<T>;
}
