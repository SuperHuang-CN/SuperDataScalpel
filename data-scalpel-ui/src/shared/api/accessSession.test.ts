import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, getAccessToken, initializeAccessSession, saveAccessToken, subscribeAccessSession } from './accessSession';

const key = 'data-scalpel.auth-session';
const legacyKey = 'data-scalpel.access-token';
beforeEach(() => { localStorage.clear(); sessionStorage.clear(); });
afterEach(() => { vi.useRealTimers(); localStorage.clear(); sessionStorage.clear(); });

describe('shared browser login', () => {
  it('stores the login and expiry together and removes the tab-only token', () => {
    sessionStorage.setItem(legacyKey, 'old');
    const expiry = new Date(Date.now() + 60_000).toISOString();
    saveAccessToken('new', expiry);
    expect(JSON.parse(localStorage.getItem(key)!)).toEqual({ accessToken: 'new', expiresAt: Date.parse(expiry) });
    expect(sessionStorage.getItem(legacyKey)).toBeNull();
    expect(getAccessToken()).toBe('new');
  });

  it('migrates a legacy login once without reviving it after another tab signs out', () => {
    sessionStorage.setItem(legacyKey, 'old');
    initializeAccessSession();
    expect(getAccessToken()).toBe('old');
    clearAccessToken();
    sessionStorage.setItem(legacyKey, 'stale-tab');
    initializeAccessSession();
    expect(getAccessToken()).toBeNull();
    expect(sessionStorage.getItem(legacyKey)).toBeNull();
  });

  it('never overwrites the shared account with an old tab token', () => {
    saveAccessToken('new-user');
    sessionStorage.setItem(legacyKey, 'old-user');
    initializeAccessSession();
    expect(getAccessToken()).toBe('new-user');
  });

  it('honors legacy JWT expiry', () => {
    const token = `header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) - 10 }))}.signature`;
    sessionStorage.setItem(legacyKey, token);
    initializeAccessSession();
    expect(getAccessToken()).toBeNull();
  });

  it('expires an idle session and notifies the current tab', () => {
    vi.useFakeTimers();
    saveAccessToken('short-lived', new Date(Date.now() + 1000).toISOString());
    const changed = vi.fn(); const stop = subscribeAccessSession(changed);
    changed.mockClear(); vi.advanceTimersByTime(1001);
    expect(getAccessToken()).toBeNull();
    expect(localStorage.getItem(key)).toBe('null');
    expect(changed).toHaveBeenCalled(); stop();
  });

  it('observes other tabs and reads the latest value, not an out-of-order event value', () => {
    const changed = vi.fn(); const stop = subscribeAccessSession(changed);
    saveAccessToken('latest'); changed.mockClear();
    window.dispatchEvent(new StorageEvent('storage', { key, storageArea: localStorage, newValue: 'outdated' }));
    expect(changed).toHaveBeenCalledOnce(); expect(getAccessToken()).toBe('latest');
    stop(); changed.mockClear(); saveAccessToken('next'); expect(changed).not.toHaveBeenCalled();
  });

  it('does not remove a newer session in response to an older request failure', () => {
    saveAccessToken('new'); clearAccessToken('old'); expect(getAccessToken()).toBe('new');
    clearAccessToken('new'); expect(getAccessToken()).toBeNull();
  });

  it('rejects malformed stored sessions', () => {
    localStorage.setItem(key, '{invalid'); expect(getAccessToken()).toBeNull();
    localStorage.setItem(key, JSON.stringify({ accessToken:'token', expiresAt:'never' })); expect(getAccessToken()).toBeNull();
  });
});
