import { afterEach, describe, expect, it, vi } from 'vitest';
import { createUuid } from './createUuid';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('createUuid', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('creates unique RFC 4122 version 4 values with Web Crypto', () => {
    const values = new Set(Array.from({ length: 1_000 }, createUuid));
    expect(values.size).toBe(1_000);
    for (const value of values) expect(value).toMatch(UUID_V4);
  });

  it('keeps producing valid values when Web Crypto is unavailable', () => {
    vi.stubGlobal('crypto', undefined);
    const first = createUuid();
    const second = createUuid();
    expect(first).toMatch(UUID_V4);
    expect(second).toMatch(UUID_V4);
    expect(second).not.toBe(first);
  });
});
