let fallbackCounter = 0;

const fillFallbackEntropy = (bytes: Uint8Array) => {
  fallbackCounter = (fallbackCounter + 1) >>> 0;
  const highResolutionTime = typeof globalThis.performance?.now === 'function'
    ? Math.floor(globalThis.performance.now() * 1_000)
    : 0;
  let state = (Date.now() ^ highResolutionTime ^ Math.imul(fallbackCounter, 0x9e3779b9)) >>> 0;
  for (let index = 0; index < bytes.length; index += 1) {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    bytes[index] = (state + Math.floor(Math.random() * 256)) & 0xff;
  }
};

/**
 * Creates an RFC 4122 UUID v4 for client-side entity keys and idempotency IDs.
 * The fallback maximizes compatibility in legacy/insecure browser contexts and
 * must never be used for credentials, access tokens, or other secrets.
 */
export const createUuid = () => {
  const bytes = new Uint8Array(16);
  const cryptoApi = globalThis.crypto;
  if (cryptoApi && typeof cryptoApi.getRandomValues === 'function') cryptoApi.getRandomValues(bytes);
  else fillFallbackEntropy(bytes);

  bytes[6] = (bytes[6]! & 0x0f) | 0x40;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};
