const SESSION_KEY = 'data-scalpel.auth-session';
const LEGACY_TOKEN_KEY = 'data-scalpel.access-token';
const SESSION_EVENT = 'data-scalpel:auth-session-changed';

interface AccessSession {
  accessToken: string;
  expiresAt: number | null;
}

const readSession = (): AccessSession | null => {
  try {
    const value: unknown = JSON.parse(window.localStorage.getItem(SESSION_KEY) ?? 'null');
    if (!value || typeof value !== 'object' || !('accessToken' in value) || !('expiresAt' in value)) return null;
    if (typeof value.accessToken !== 'string' || !value.accessToken) return null;
    if (value.expiresAt !== null && (typeof value.expiresAt !== 'number' || !Number.isFinite(value.expiresAt))) return null;
    return { accessToken: value.accessToken, expiresAt: value.expiresAt };
  } catch {
    return null;
  }
};

const tokenExpiry = (token: string): number | null => {
  try {
    const payload: unknown = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    if (payload && typeof payload === 'object' && 'exp' in payload && typeof payload.exp === 'number' && Number.isFinite(payload.exp)) return payload.exp * 1000;
  } catch {
    // Only used for the old sessionStorage token; the server still verifies JWTs.
  }
  return null;
};

export const getAccessToken = (): string | null => {
  const session = readSession();
  return session && (session.expiresAt === null || session.expiresAt > Date.now()) ? session.accessToken : null;
};

export const hasAccessToken = (): boolean => Boolean(getAccessToken());

export const saveAccessToken = (accessToken: string, expiresAt?: string): void => {
  const expiry = expiresAt === undefined ? tokenExpiry(accessToken) : Date.parse(expiresAt);
  if (expiry !== null && !Number.isFinite(expiry)) throw new Error('登录有效期无效，请重新登录');
  window.localStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken, expiresAt: expiry }));
  window.sessionStorage.removeItem(LEGACY_TOKEN_KEY);
  window.dispatchEvent(new Event(SESSION_EVENT));
};

export const clearAccessToken = (expectedToken?: string): void => {
  // An old request's delayed 401 must not sign out a newer login.
  if (expectedToken !== undefined && readSession()?.accessToken !== expectedToken) return;
  // Keep an explicit signed-out record so an old tab cannot resurrect its legacy token.
  window.localStorage.setItem(SESSION_KEY, 'null');
  window.sessionStorage.removeItem(LEGACY_TOKEN_KEY);
  window.dispatchEvent(new Event(SESSION_EVENT));
};

export const initializeAccessSession = (): void => {
  const legacy = window.sessionStorage.getItem(LEGACY_TOKEN_KEY);
  window.sessionStorage.removeItem(LEGACY_TOKEN_KEY);
  if (window.localStorage.getItem(SESSION_KEY) === null && legacy) saveAccessToken(legacy);
};

export const subscribeAccessSession = (onChange: () => void): (() => void) => {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const update = () => {
    window.clearTimeout(timer);
    const session = readSession();
    if (session?.expiresAt !== null && session?.expiresAt !== undefined) {
      const remaining = session.expiresAt - Date.now();
      if (remaining <= 0) {
        clearAccessToken(session.accessToken);
        return;
      }
      timer = window.setTimeout(update, Math.min(remaining, 2_147_483_647));
    }
    onChange();
  };
  const onStorage = (event: StorageEvent) => {
    if (event.storageArea === window.localStorage && (event.key === SESSION_KEY || event.key === null)) update();
  };
  window.addEventListener('storage', onStorage);
  window.addEventListener(SESSION_EVENT, update);
  window.addEventListener('focus', update);
  document.addEventListener('visibilitychange', update);
  update();
  return () => {
    window.clearTimeout(timer);
    window.removeEventListener('storage', onStorage);
    window.removeEventListener(SESSION_EVENT, update);
    window.removeEventListener('focus', update);
    document.removeEventListener('visibilitychange', update);
  };
};
