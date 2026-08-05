const API_BASE = import.meta.env.VITE_GATEWAY_ADMIN_API ?? 'http://localhost:19000/admin-api/v1';
const TOKEN_KEY = 'super-api-gateway-token';

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly problem?: Record<string, unknown>) {
    super(typeof problem?.detail === 'string' ? problem.detail : `请求失败 (${status})`);
  }
}

export const authToken = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const token = authToken.get();
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => undefined);
    if (response.status === 401 && path !== '/auth/login') authToken.clear();
    throw new ApiError(response.status, problem);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const post = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
