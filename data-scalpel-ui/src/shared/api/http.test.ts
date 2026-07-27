import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, clearAccessToken, requestBlob, requestJson, saveAccessToken } from './http';

const successfulResponse = () => ({
  ok: true,
  status: 200,
  text: async () => '{"ok":true}',
}) as Response;

describe('requestJson', () => {
  beforeEach(() => {
    clearAccessToken();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(successfulResponse()));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    clearAccessToken();
  });

  it('attaches the session token to protected requests', async () => {
    saveAccessToken('session-token');

    await requestJson('/v1/protected');

    expect(fetch).toHaveBeenCalledWith('/api/v1/protected', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer session-token' }),
    }));
  });

  it('does not send a stale token to a public authentication request', async () => {
    saveAccessToken('expired-token');

    await requestJson('/v1/auth/login', { method: 'POST', skipAuthentication: true });

    expect(fetch).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({
      headers: expect.not.objectContaining({ Authorization: expect.anything() }),
    }));
  });

  it('lets the browser provide the multipart boundary for FormData requests', async () => {
    const body = new FormData();
    body.append('request', new Blob(['{}'], { type: 'application/json' }));

    await requestJson('/v1/file-datasets', { method: 'POST', body });

    expect(fetch).toHaveBeenCalledWith('/api/v1/file-datasets', expect.objectContaining({
      body,
      headers: expect.not.objectContaining({ 'Content-Type': expect.anything() }),
    }));
  });

  it('retains complete problem details from a failed API request', async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 400,
      text: async () => JSON.stringify({
        type: 'urn:datascalpel:problem:validation-failed',
        title: '请求参数校验失败',
        status: 400,
        detail: '请求参数校验失败',
        instance: '/api/v1/data-sources',
        code: 'VALIDATION_FAILED',
        timestamp: '2026-07-14T12:00:00Z',
        violations: [{ field: 'name', message: '不能为空' }],
      }),
    } as Response);

    const error = await requestJson('/v1/data-sources').catch((failure: unknown) => failure);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      message: '请求参数校验失败',
      status: 400,
      problem: {
        code: 'VALIDATION_FAILED',
        instance: '/api/v1/data-sources',
        violations: [{ field: 'name', message: '不能为空' }],
      },
    });
  });

  it('combines a caller AbortSignal with the internal request timeout', async () => {
    vi.mocked(fetch).mockImplementation((_input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
    }));
    const caller = new AbortController();

    const request = requestJson('/v1/task-compilations', { signal: caller.signal }, 60_000);
    caller.abort();

    await expect(request).rejects.toMatchObject({ name: 'AbortError' });
    expect(vi.mocked(fetch).mock.calls[0][1]?.signal).not.toBe(caller.signal);
    expect(vi.mocked(fetch).mock.calls[0][1]?.signal?.aborted).toBe(true);
  });

  it('reports the internal timeout as an API failure instead of a caller cancellation', async () => {
    vi.mocked(fetch).mockImplementation((_input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
    }));

    const request = requestJson('/v1/task-compilations', {}, 1).catch((error: unknown) => error);

    await expect(request).resolves.toMatchObject({
      name: 'ApiError',
      message: '请求超时，请稍后重试',
    });
  });

  it('downloads protected binary content with the session token', async () => {
    saveAccessToken('session-token');
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      status: 200,
      blob: async () => new Blob(['orders']),
    } as Response);

    const result = await requestBlob('/v1/file-datasets/id/content');

    expect(result.size).toBe(6);
    expect(fetch).toHaveBeenCalledWith('/api/v1/file-datasets/id/content', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer session-token' }),
    }));
  });
});
