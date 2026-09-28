import { afterEach, expect, it, vi } from 'vitest';
import type { MessageConnection } from 'vscode-jsonrpc/browser';
import { prepareJavaLanguage } from './prepareJavaLanguage';

afterEach(() => { vi.useRealTimers(); });
function connection() {
  const sendRequest = vi.fn().mockResolvedValueOnce({ items: [{ label: 'String()' }] })
    .mockResolvedValueOnce({ items: [{ label: 'models() : ModelResources' }] });
  const sendNotification = vi.fn().mockResolvedValue(undefined);
  return { sendRequest, sendNotification, value: { sendRequest, sendNotification } as unknown as MessageConnection };
}
it('checks real Java and SDK candidates without changing saved source', async () => {
  const h = connection();
  await prepareJavaLanguage(h.value, new AbortController().signal);
  expect(h.sendRequest).toHaveBeenCalledTimes(2);
  expect(h.sendNotification).toHaveBeenCalledOnce();
  expect(h.sendNotification.mock.calls[0][1].textDocument.uri).toContain('__DataScalpelLanguageProbe');
});
it('retries only incomplete analysis and finishes immediately when candidates exist', async () => {
  const h = connection();
  h.sendRequest.mockReset().mockResolvedValueOnce({ isIncomplete: true, items: [] })
    .mockResolvedValueOnce({ items: [{ label: 'String()' }] }).mockResolvedValueOnce([{ label: 'models()' }]);
  await prepareJavaLanguage(h.value, new AbortController().signal);
  expect(h.sendRequest).toHaveBeenCalledTimes(3);
});
it('reports missing dependencies rather than claiming ready', async () => {
  const h = connection(); h.sendRequest.mockReset().mockResolvedValue({ isIncomplete: false, items: [] });
  await expect(prepareJavaLanguage(h.value, new AbortController().signal)).rejects.toThrow('dependencies');
  expect(h.sendRequest).toHaveBeenCalledOnce();
});
it('bounds both incomplete loops and stalled analysis', async () => {
  const h = connection(); h.sendRequest.mockReset().mockResolvedValue({ isIncomplete: true, items: [] });
  await expect(prepareJavaLanguage(h.value, new AbortController().signal)).rejects.toThrow('indexing');
  expect(h.sendRequest).toHaveBeenCalledTimes(12);
  vi.useFakeTimers();
  h.sendRequest.mockImplementation(() => new Promise(() => undefined));
  const result = expect(prepareJavaLanguage(h.value, new AbortController().signal)).rejects.toThrow('stopped');
  await vi.advanceTimersByTimeAsync(60_000); await result;
  expect(h.sendRequest.mock.calls.at(-1)?.[2].isCancellationRequested).toBe(true);
});
it('cancels when the editor closes', async () => {
  const h = connection(); h.sendRequest.mockReset().mockImplementation(() => new Promise(() => undefined));
  const controller = new AbortController();
  const result = expect(prepareJavaLanguage(h.value, controller.signal)).rejects.toThrow('stopped');
  controller.abort(); await result;
});
