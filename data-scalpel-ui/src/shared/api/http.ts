const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';
const DEFAULT_TIMEOUT_MS = 20_000;
const ACCESS_TOKEN_STORAGE_KEY = 'data-scalpel.access-token';

export const hasAccessToken = (): boolean => Boolean(window.sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY));

export const saveAccessToken = (accessToken: string): void => {
  window.sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, accessToken);
};

export const clearAccessToken = (): void => {
  window.sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
};

const authorizationHeader = (): Record<string, string> => {
  const accessToken = window.sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
};

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface JsonRequestInit extends RequestInit {
  skipAuthentication?: boolean;
}

export const requestJson = async <T>(
  path: string,
  init: JsonRequestInit = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> => {
  const { skipAuthentication = false, ...requestInit } = init;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...requestInit,
      headers: {
        Accept: 'application/json',
        ...(skipAuthentication ? {} : authorizationHeader()),
        ...(requestInit.body ? { 'Content-Type': 'application/json' } : {}),
        ...requestInit.headers,
      },
      signal: controller.signal,
    });
    const body = await response.text();

    if (!response.ok) {
      throw new ApiError(toErrorMessage(body, response.status), response.status);
    }

    return body ? JSON.parse(body) as T : undefined as T;
  } finally {
    window.clearTimeout(timeout);
  }
};

const toErrorMessage = (body: string, status: number): string => {
  if (!body) return `请求失败（HTTP ${status}）`;

  try {
    const problem = JSON.parse(body) as { detail?: unknown };
    if (typeof problem.detail === 'string' && problem.detail) return problem.detail;
  } catch {
    // Non-JSON error responses still retain their readable response body.
  }

  return body;
};
