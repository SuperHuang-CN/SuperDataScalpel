const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';
const DEFAULT_TIMEOUT_MS = 20_000;
const ACCESS_TOKEN_STORAGE_KEY = 'data-scalpel.access-token';

export interface ApiViolation {
  field: string;
  message: string;
}

/** RFC 9457 problem details returned by DataScalpel HTTP APIs. */
export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  timestamp?: string;
  violations?: ApiViolation[];
}

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
    readonly problem?: ApiProblem,
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
  const callerSignal = requestInit.signal;
  const abortFromCaller = () => controller.abort();
  if (callerSignal?.aborted) controller.abort();
  else callerSignal?.addEventListener('abort', abortFromCaller, { once: true });
  let timedOut = false;
  const timeout = window.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...requestInit,
      headers: {
        Accept: 'application/json',
        ...(skipAuthentication ? {} : authorizationHeader()),
        ...(requestInit.body && !(requestInit.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
        ...requestInit.headers,
      },
      signal: controller.signal,
    });
    const body = await response.text();

    if (!response.ok) {
      throw toApiError(body, response.status);
    }

    return body ? JSON.parse(body) as T : undefined as T;
  } catch (error: unknown) {
    if (timedOut && error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('请求超时，请稍后重试');
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
    callerSignal?.removeEventListener('abort', abortFromCaller);
  }
};

export const requestBlob = async (
  path: string,
  init: JsonRequestInit = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<Blob> => {
  const response = await requestBlobResponse(path, init, timeoutMs);
  return response.blob;
};

export interface BlobResponse {
  blob: Blob;
  headers: Headers;
}

export const requestBlobResponse = async (
  path: string,
  init: JsonRequestInit = {},
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<BlobResponse> => {
  const { skipAuthentication = false, ...requestInit } = init;
  const controller = new AbortController();
  const callerSignal = requestInit.signal;
  const abortFromCaller = () => controller.abort();
  if (callerSignal?.aborted) controller.abort();
  else callerSignal?.addEventListener('abort', abortFromCaller, { once: true });
  let timedOut = false;
  const timeout = window.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...requestInit,
      headers: {
        ...(skipAuthentication ? {} : authorizationHeader()),
        ...requestInit.headers,
      },
      signal: controller.signal,
    });
    if (!response.ok) {
      throw toApiError(await response.text(), response.status);
    }
    return { blob: await response.blob(), headers: response.headers };
  } catch (error: unknown) {
    if (timedOut && error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('请求超时，请稍后重试');
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
    callerSignal?.removeEventListener('abort', abortFromCaller);
  }
};

const toApiError = (body: string, status: number): ApiError => {
  if (!body) return new ApiError(`请求失败（HTTP ${status}）`, status);

  try {
    const value: unknown = JSON.parse(body);
    if (typeof value === 'object' && value !== null) {
      const problem = value as ApiProblem;
      const message = typeof problem.detail === 'string' && problem.detail
        ? problem.detail
        : body;
      return new ApiError(message, status, problem);
    }
  } catch {
    // Non-JSON error responses still retain their readable response body.
  }

  return new ApiError(body, status);
};
