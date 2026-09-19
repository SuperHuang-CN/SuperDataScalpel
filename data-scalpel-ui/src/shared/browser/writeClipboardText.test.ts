import { afterEach, describe, expect, it, vi } from 'vitest';
import { writeClipboardText } from './writeClipboardText';

describe('writeClipboardText', () => {
  const originalExecCommand = Object.getOwnPropertyDescriptor(globalThis.document, 'execCommand');

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    if (originalExecCommand) Object.defineProperty(globalThis.document, 'execCommand', originalExecCommand);
    else Reflect.deleteProperty(globalThis.document, 'execCommand');
  });

  it('uses the modern clipboard API when available', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    await writeClipboardText('hello');
    expect(writeText).toHaveBeenCalledWith('hello');
  });

  it('falls back to execCommand when the modern API rejects LAN HTTP', async () => {
    const writeText = vi.fn().mockRejectedValue(new DOMException('blocked', 'NotAllowedError'));
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const execCommand = vi.fn().mockReturnValue(true);
    Object.defineProperty(globalThis.document, 'execCommand', { configurable: true, value: execCommand });
    await writeClipboardText('fallback');
    expect(execCommand).toHaveBeenCalledWith('copy');
    expect(globalThis.document.querySelector('textarea[aria-hidden="true"]')).toBeNull();
  });
});
