import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, requestJson, saveAccessToken } from './http';

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
});
