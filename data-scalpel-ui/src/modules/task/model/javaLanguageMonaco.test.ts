import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type * as Monaco from 'monaco-editor';
import { CancellationTokenSource } from 'vscode-jsonrpc/browser';
import { attachJavaLanguage } from './javaLanguageMonaco';

const transport = vi.hoisted(() => ({ connect: vi.fn() }));
const preparation = vi.hoisted(() => ({ prepare: vi.fn() }));
const checker = vi.hoisted(() => ({ check: vi.fn() }));
vi.mock('../api/taskApi', () => ({ checkSparkJarOnlineSource: checker.check }));
vi.mock('./prepareJavaLanguage', () => ({ prepareJavaLanguage: preparation.prepare }));
vi.mock('./javaLanguageConnection', () => ({ connectJavaLanguage: transport.connect, newEditorSessionId: () => 'editor' }));
vi.mock('./javaEntry', () => ({ javaEntry: (_monaco: unknown, source: string) => ({ uri: 'file:///datascalpel/src/Job.java', fileName: 'Job.java', offset: source.indexOf('Job') }),
  visibleJavaSource: (_monaco: unknown, source: string) => source }));

function harness(source = 'class Job { void run() { new Strin } }') {
  const notifications = new Map<string, (value: unknown) => void>();
  const providers: Record<string, unknown> = {};
  let change: () => void = () => undefined;
  let version = 1;
  const disposable = { dispose: vi.fn() };
  const connection = {
    onClose: vi.fn(), onError: vi.fn(), listen: vi.fn(),
    onNotification: (method: string, handler: (value: unknown) => void) => notifications.set(method, handler),
    sendNotification: vi.fn().mockResolvedValue(undefined),
    sendRequest: vi.fn().mockImplementation((method: string) => Promise.resolve(method === 'initialize' ? { capabilities: {} } : { items: [], isIncomplete: true })),
  };
  const close = vi.fn();
  transport.connect.mockResolvedValue({ connection, close });
  const model = {
    getValue: () => source, getVersionId: () => version, getOffsetAt: (cursor: Monaco.Position) => cursor.column - 1,
    isDisposed: () => false, getWordUntilPosition: () => ({ word: 'Strin', startColumn: 29, endColumn: 34 }),
    onDidChangeContent: (handler: () => void) => { change = handler; return disposable; },
  } as unknown as Monaco.editor.ITextModel;
  const register = (name: string) => (_language: string, provider: unknown) => { providers[name] = provider; return disposable; };
  const monaco = {
    editor: { setModelMarkers: vi.fn() }, MarkerSeverity: { Error: 8, Warning: 4, Info: 2 },
    Range: class { constructor(public startLineNumber: number, public startColumn: number, public endLineNumber: number, public endColumn: number) {} },
    languages: {
      CompletionItemKind: { Constructor: 2 }, CompletionItemInsertTextRule: { InsertAsSnippet: 4 },
      registerCompletionItemProvider: register('completion'), registerHoverProvider: register('hover'),
      registerSignatureHelpProvider: register('signature'), registerDocumentFormattingEditProvider: register('format'),
      registerRenameProvider: register('rename'),
    },
  } as unknown as typeof Monaco;
  const status = vi.fn();
  const language = attachJavaLanguage(monaco, model, 'task', 'Job.java', status);
  const provider = providers.completion as Monaco.languages.CompletionItemProvider;
  const complete = (cancel = new CancellationTokenSource()) => Promise.resolve(provider.provideCompletionItems(model,
    { lineNumber: 1, column: 34 } as Monaco.Position, { triggerKind: 0 }, cancel.token));
  return { language, connection, close, status, notifications, provider, complete, markers: monaco.editor.setModelMarkers, edit: () => { version++; change(); } };
}

beforeEach(() => { vi.useFakeTimers(); transport.connect.mockReset(); preparation.prepare.mockReset().mockResolvedValue(undefined); checker.check.mockReset().mockResolvedValue({ status: 'SUCCEEDED' }); });
afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); });

describe('Java semantic completion scheduling', () => {
  it('resets the failure budget after real readiness, including idle reauthentication cycles', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    for (let cycle = 0; cycle < 5; cycle++) {
      expect(h.language.ready).toBe(true);
      h.connection.onClose.mock.calls.at(-1)?.[0]();
      expect(h.language.ready).toBe(false);
      await vi.advanceTimersByTimeAsync(2000);
      expect(h.language.ready).toBe(true);
    }
    expect(transport.connect).toHaveBeenCalledTimes(6);
    // No user completion/hover request is required to restore the retry budget.
    expect(h.connection.sendRequest.mock.calls.filter(([method]) => method === 'textDocument/completion')).toHaveLength(0);
    h.language.dispose();
  });

  it('bounds consecutive reconnect failures and allows an explicit retry', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    transport.connect.mockRejectedValue(new Error('offline'));
    h.connection.onClose.mock.calls.at(-1)?.[0]();
    await vi.advanceTimersByTimeAsync(120000);
    expect(transport.connect).toHaveBeenCalledTimes(4);
    expect(h.language.ready).toBe(false);
    expect(h.status.mock.calls.at(-1)?.[0].message).toContain('登录状态或网络');
    transport.connect.mockResolvedValue({ connection: h.connection, close: h.close });
    h.language.retry(); await vi.advanceTimersByTimeAsync(0);
    expect(h.language.ready).toBe(true);
    h.language.dispose();
  });

  it('does not consume retries while a failed preparation is still unwinding', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    let reject!: (reason: Error) => void;
    preparation.prepare.mockImplementationOnce(() => new Promise<void>((_resolve, fail) => { reject = fail; }));
    h.connection.onClose.mock.calls.at(-1)?.[0]();
    await vi.advanceTimersByTimeAsync(2000);
    h.notifications.get('datascalpel/status')?.({ code: 'JAVA_LANGUAGE_EXITED' });
    await vi.advanceTimersByTimeAsync(10000);
    expect(transport.connect).toHaveBeenCalledTimes(2);
    expect(h.status.mock.calls.at(-1)?.[0].message).toContain('语义进程已退出');
    reject(new Error('disposed'));
    await vi.advanceTimersByTimeAsync(2000);
    expect(h.language.ready).toBe(true);
    expect(transport.connect).toHaveBeenCalledTimes(3);
    h.language.dispose();
  });

  it('cancels scheduled recovery when the editor is disposed', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    h.connection.onClose.mock.calls.at(-1)?.[0]();
    h.language.dispose(); await vi.advanceTimersByTimeAsync(60000);
    expect(transport.connect).toHaveBeenCalledOnce();
  });

  const wildcard = 'import static org.apache.spark.sql.functions.*;';
  function renameHarness() {
    const h = harness('public class Job {}\n' + wildcard);
    const selectionRange = { start: { line: 0, character: 13 }, end: { line: 0, character: 16 } };
    const respond = (method: string) => method === 'textDocument/rename' ? { changes: {} }
      : method === 'textDocument/documentHighlight' ? [{ range: selectionRange }]
      : method === 'textDocument/documentSymbol' ? [{ name: 'Job', kind: 5, selectionRange, range: selectionRange }] : null;
    h.connection.sendRequest.mockImplementation((method: string) => Promise.resolve(respond(method)));
    return { ...h, respond, rename: (cancel?: CancellationTokenSource) => h.language.rename({ lineNumber: 1, column: 14 } as Monaco.Position, 'MeterJob', cancel?.token) };
  }

  it('recovers an empty Spark-wildcard rename using semantic ranges and a read-only check', async () => {
    const h = renameHarness(); await vi.advanceTimersByTimeAsync(0);
    const result = await h.rename();
    expect(result.rejectReason).toBeUndefined(); expect(result.edits).toHaveLength(1);
    expect(checker.check).toHaveBeenCalledWith('task', 'public class MeterJob {}\n' + wildcard);
    h.language.dispose();
  });

  it('does not fall back after an explicit failure or for an unrelated symbol', async () => {
    const h = renameHarness(); await vi.advanceTimersByTimeAsync(0);
    h.connection.sendRequest.mockResolvedValue(null);
    expect((await h.rename()).rejectReason).toContain('未取得');
    h.connection.sendRequest.mockResolvedValue({ changes: {} });
    expect((await h.language.rename({ lineNumber: 1, column: 1 } as Monaco.Position, 'MeterJob')).rejectReason).toContain('源码未改变');
    expect(checker.check).not.toHaveBeenCalled(); h.language.dispose();
  });

  it.each(['FAILED', 'network'])('leaves the source unchanged when candidate checking returns %s', async (failure) => {
    const h = renameHarness(); await vi.advanceTimersByTimeAsync(0);
    if (failure === 'network') checker.check.mockRejectedValue(new Error('连接失败'));
    else checker.check.mockResolvedValue({ status: failure });
    const result = await h.rename(); expect(result.edits).toEqual([]); expect(result.rejectReason).toBeTruthy();
    h.language.dispose();
  });

  it.each(['edit', 'cancel', 'dispose'])('rejects a candidate made stale by %s during checking', async (action) => {
    const h = renameHarness(); await vi.advanceTimersByTimeAsync(0);
    let finish!: (value: unknown) => void;
    checker.check.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    const cancel = new CancellationTokenSource();
    const result = h.rename(cancel); await vi.advanceTimersByTimeAsync(0);
    if (action === 'edit') h.edit(); else if (action === 'cancel') cancel.cancel(); else h.language.dispose();
    finish({ status: 'SUCCEEDED' });
    expect((await result).edits).toEqual([]); h.language.dispose();
  });

  it('retains current-source diagnostics received before the readiness barrier completes', async () => {
    let finish!: () => void;
    preparation.prepare.mockImplementation(() => new Promise<void>((resolve) => { finish = resolve; }));
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    let analyze!: (value: null) => void;
    h.connection.sendRequest.mockImplementation(() => new Promise((resolve) => { analyze = resolve; }));
    finish(); await vi.advanceTimersByTimeAsync(0);
    h.notifications.get('textDocument/publishDiagnostics')?.({ uri: 'file:///datascalpel/src/Job.java', version: 1,
      diagnostics: [{ message: 'Initial type error', severity: 1, range: { start: { line: 0, character: 0 }, end: { line: 0, character: 4 } } }] });
    expect(h.language.ready).toBe(false);
    analyze(null); await vi.advanceTimersByTimeAsync(0);
    expect(h.markers).toHaveBeenCalledWith(expect.anything(), 'datascalpel-jdt', expect.arrayContaining([expect.objectContaining({ message: 'Initial type error' })]));
    h.language.dispose();
  });

  it('exposes semantic rename edits and an explicit error when rename is unavailable', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    h.connection.sendRequest.mockResolvedValue({ changes: { 'file:///datascalpel/src/Job.java': [
      { range: { start: { line: 0, character: 6 }, end: { line: 0, character: 9 } }, newText: 'RenamedJob' },
    ] } });
    const renamed = await h.language.rename({ lineNumber: 1, column: 7 } as Monaco.Position, 'RenamedJob');
    expect(renamed.edits).toHaveLength(1);
    expect(renamed.rejectReason).toBeUndefined();
    expect((await h.language.rename({ lineNumber: 1, column: 7 } as Monaco.Position, 'Bad Name')).rejectReason).toContain('有效');
    h.connection.sendRequest.mockResolvedValue(null);
    expect((await h.language.rename({ lineNumber: 1, column: 7 } as Monaco.Position, 'OtherJob')).rejectReason).toContain('未取得');
    h.connection.sendRequest.mockResolvedValue({ changes: {} });
    expect((await h.language.rename({ lineNumber: 1, column: 7 } as Monaco.Position, 'OtherJob')).rejectReason).toContain('源码未改变');
    h.language.dispose();
  });

  it('honors superseded completion cancellation and preserves insertion and import edits', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    h.connection.sendRequest.mockResolvedValue({ items: [{ label: 'String()', kind: 4, insertTextFormat: 2,
      textEdit: { range: { start: { line: 0, character: 28 }, end: { line: 0, character: 33 } }, newText: 'String($0)' },
      additionalTextEdits: [{ range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } }, newText: 'import java.util.List;\n' }],
    }], isIncomplete: true });
    const cancel = new CancellationTokenSource();
    const old = h.complete(cancel); cancel.cancel(); h.edit(); const latest = h.complete();
    await vi.advanceTimersByTimeAsync(60);
    expect((await old)?.suggestions).toHaveLength(0);
    const result = await latest;
    expect(result?.suggestions[0].insertText).toBe('String($0)');
    expect(result?.suggestions[0].additionalTextEdits?.[0].text).toContain('import');
    expect(result?.incomplete).toBe(true);
    expect(h.connection.sendRequest.mock.calls.filter(([method]) => method === 'textDocument/completion')).toHaveLength(1);
    expect(h.provider.triggerCharacters).toContain(' ');
    h.language.dispose();
  });

  it('immediately releases stale UI requests and rejects their eventual results', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    let respond!: (value: unknown) => void;
    h.connection.sendRequest.mockImplementation(() => new Promise((resolve) => { respond = resolve; }));
    const cancel = new CancellationTokenSource();
    const result = h.complete(cancel); await vi.advanceTimersByTimeAsync(60);
    const token = h.connection.sendRequest.mock.calls.at(-1)?.[2] as Monaco.CancellationToken;
    h.edit(); cancel.cancel(); await vi.advanceTimersByTimeAsync(0);
    expect(token.isCancellationRequested).toBe(true);
    expect((await result)?.suggestions).toHaveLength(0);
    respond({ items: [{ label: 'outdated' }] }); await vi.advanceTimersByTimeAsync(0);
    expect(h.close).not.toHaveBeenCalled();
    h.language.dispose();
  });

  it('keeps a slow session alive, but bounds requests ignored by the server', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    h.connection.sendRequest.mockImplementation(() => new Promise(() => undefined));
    const result = h.complete(); await vi.advanceTimersByTimeAsync(15_060);
    expect((await result)?.suggestions).toHaveLength(0);
    expect(h.close).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(45_000);
    expect(h.close).toHaveBeenCalledOnce();
    h.language.dispose();
  });

  it('does not unlock on connection or ServiceReady before actual completion preparation', async () => {
    let finish!: () => void;
    preparation.prepare.mockImplementation(() => new Promise<void>((resolve) => { finish = resolve; }));
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    expect(h.language.ready).toBe(false);
    h.notifications.get('language/status')?.({ type: 'ServiceReady' });
    expect(h.language.ready).toBe(false);
    finish(); await vi.advanceTimersByTimeAsync(0);
    expect(h.language.ready).toBe(true);
    expect(h.status.mock.calls.at(-1)?.[0].message).toBe('Java 语义提示已就绪');
    h.language.dispose();
  });

  it('surfaces preparation failure without repeatedly restarting the cold session', async () => {
    preparation.prepare.mockRejectedValueOnce(new Error('unavailable'));
    const h = harness(); await vi.advanceTimersByTimeAsync(20_000);
    expect(h.language.ready).toBe(false);
    expect(h.status.mock.calls.at(-1)?.[0].retryable).toBe(true);
    expect(transport.connect).toHaveBeenCalledOnce();
    h.language.retry(); await vi.advanceTimersByTimeAsync(0);
    expect(h.language.ready).toBe(true);
    h.language.dispose();
  });

  it('retries an empty incomplete result, but never loops on a real empty result', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    h.connection.sendRequest.mockResolvedValueOnce({ items: [], isIncomplete: true })
      .mockResolvedValueOnce({ items: [{ label: 'String()', insertText: 'String()' }], isIncomplete: true });
    const result = h.complete(); await vi.advanceTimersByTimeAsync(60);
    expect((await result)?.suggestions[0].label).toBe('String()');
    h.connection.sendRequest.mockClear().mockResolvedValue({ items: [], isIncomplete: false });
    const empty = h.complete(); await vi.advanceTimersByTimeAsync(60);
    expect((await empty)?.suggestions).toHaveLength(0);
    expect(h.connection.sendRequest).toHaveBeenCalledOnce();
    h.connection.sendRequest.mockClear().mockResolvedValue({ items: [], isIncomplete: true });
    const indexing = h.complete(); await vi.advanceTimersByTimeAsync(60);
    await indexing;
    expect(h.connection.sendRequest).toHaveBeenCalledTimes(3);
    h.language.dispose();
  });

  it('never exceeds eight outstanding server requests even when cancellation is ignored', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    h.connection.sendRequest.mockClear().mockImplementation(() => new Promise(() => undefined));
    for (let i = 0; i < 10; i++) {
      const cancel = new CancellationTokenSource();
      const pending = h.complete(cancel); await vi.advanceTimersByTimeAsync(60);
      h.edit(); cancel.cancel(); await vi.advanceTimersByTimeAsync(0); await pending;
    }
    expect(h.connection.sendRequest).toHaveBeenCalledTimes(8);
    h.language.dispose();
  });

  it('keeps dot completion alive while Monaco filters a growing word', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    let respond!: (value: unknown) => void;
    h.connection.sendRequest.mockImplementation(() => new Promise((resolve) => { respond = resolve; }));
    const result = h.complete(); await vi.advanceTimersByTimeAsync(0);
    const token = h.connection.sendRequest.mock.calls.at(-1)?.[2] as Monaco.CancellationToken;
    h.edit(); h.edit();
    expect(token.isCancellationRequested).toBe(false);
    respond({ items: [{ label: 'trim()', insertText: 'trim()' }], isIncomplete: true });
    await vi.advanceTimersByTimeAsync(0);
    expect((await result)?.suggestions[0].label).toBe('trim()');
    h.language.dispose();
  });

  it('allows import resolution to finish after Monaco inserts the accepted candidate', async () => {
    const h = harness(); await vi.advanceTimersByTimeAsync(0);
    h.connection.sendRequest.mockResolvedValue({ items: [{ label: 'ArrayList()', insertText: 'ArrayList()' }] });
    const completed = h.complete(); await vi.advanceTimersByTimeAsync(0);
    const item = (await completed)!.suggestions[0];
    let respond!: (value: unknown) => void;
    h.connection.sendRequest.mockImplementation(() => new Promise((resolve) => { respond = resolve; }));
    const result = h.provider.resolveCompletionItem!(item, new CancellationTokenSource().token);
    h.edit();
    respond({ label: 'ArrayList()', additionalTextEdits: [{ range: { start: { line: 1, character: 0 }, end: { line: 1, character: 0 } }, newText: 'import java.util.ArrayList;\n' }] });
    await vi.advanceTimersByTimeAsync(0);
    expect((await result)?.additionalTextEdits?.[0].text).toContain('import java.util.ArrayList;');
    h.language.dispose();
  });
});
