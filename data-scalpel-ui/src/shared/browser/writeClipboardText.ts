const legacyCopy = (text: string) => {
  if (!globalThis.document?.body || typeof globalThis.document.execCommand !== 'function') return false;
  const textarea = globalThis.document.createElement('textarea');
  textarea.value = text;
  textarea.readOnly = true;
  textarea.setAttribute('aria-hidden', 'true');
  textarea.style.position = 'fixed';
  textarea.style.left = '-10000px';
  textarea.style.top = '0';
  globalThis.document.body.appendChild(textarea);
  try {
    textarea.focus();
    textarea.select();
    return globalThis.document.execCommand('copy');
  } finally {
    textarea.remove();
  }
};

/** Writes text in HTTPS, localhost, LAN HTTP, and older browser contexts. */
export const writeClipboardText = async (text: string) => {
  try {
    if (globalThis.navigator?.clipboard?.writeText) {
      await globalThis.navigator.clipboard.writeText(text);
      return;
    }
  } catch {
    // LAN HTTP and browser policy can reject the modern API; use the user-gesture fallback below.
  }
  if (!legacyCopy(text)) throw new Error('当前浏览器无法访问剪贴板。');
};
