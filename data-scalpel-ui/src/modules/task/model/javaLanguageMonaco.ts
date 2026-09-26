import type * as Monaco from 'monaco-editor';
import type * as Lsp from 'vscode-languageserver-protocol';
import { CancellationTokenSource } from 'vscode-jsonrpc/browser';
import { connectJavaLanguage, newEditorSessionId, type JavaLanguageTransport } from './javaLanguageConnection';
import { javaEntry, visibleJavaSource } from './javaEntry';
import { entryRenameCandidate } from './javaEntryRename';
import { checkSparkJarOnlineSource } from '../api/taskApi';
import { prepareJavaLanguage } from './prepareJavaLanguage';

export interface JavaLanguageStatus { ready: boolean; message: string; retryable: boolean }
const range = (value: Lsp.Range): Monaco.IRange => ({ startLineNumber: value.start.line + 1, startColumn: value.start.character + 1,
  endLineNumber: value.end.line + 1, endColumn: value.end.character + 1 });
const documentation = (value: string | Lsp.MarkupContent | undefined): Monaco.IMarkdownString | undefined =>
  value ? { value: typeof value === 'string' ? value : value.value, isTrusted: false, supportHtml: false } : undefined;

/** Monaco presentation adapter only. All Java semantic analysis comes from JDT LS. */
export function attachJavaLanguage(monaco: typeof Monaco, model: Monaco.editor.ITextModel, taskId: string,
  fileName: string, status: (value: JavaLanguageStatus) => void) {
  const controller = new AbortController();
  const sessionId = newEditorSessionId();
  let uri = javaEntry(monaco, model.getValue(), fileName).uri;
  const document = { uri };
  let transport: JavaLanguageTransport | undefined;
  let ready = false;
  let preparedOnce = false;
  let sentVersion = -1;
  let attempts = 0;
  let retryTimer: ReturnType<typeof setTimeout> | undefined;
  let changeTimer: ReturnType<typeof setTimeout> | undefined;
  let pending = 0;
  const activeRequests = new Map<CancellationTokenSource, boolean>();
  let completionSequence = 0;
  let connecting = false;
  const disposables: Monaco.IDisposable[] = [];
  const owns = (current: Monaco.editor.ITextModel) => current === model && ready;
  const position = (value: Monaco.Position) => ({ line: value.lineNumber - 1, character: value.column - 1 });
  const scheduleRetry = () => {
    if (!controller.signal.aborted && !connecting && preparedOnce && attempts < 3 && !retryTimer)
      retryTimer = setTimeout(() => { retryTimer = undefined; void start(); }, 2000 * Math.max(1, attempts));
  };
  const fail = (reason = '语义连接异常，请重新连接') => {
    if (controller.signal.aborted) return;
    ready = false;
    const previous = transport;
    transport = undefined;
    previous?.close();
    monaco.editor.setModelMarkers(model, 'datascalpel-jdt', []);
    status({ ready: false, message: `${reason}；${preparedOnce ? '仍可编辑、保存及检查代码' : '可重试或暂不使用语义提示'}`, retryable: true });
    scheduleRetry();
  };
  const synchronize = async () => {
    if (!ready || !transport || model.isDisposed()) return;
    const version = model.getVersionId();
    if (sentVersion === version) return;
    const nextUri = javaEntry(monaco, model.getValue(), fileName).uri;
    sentVersion = version;
    if (nextUri !== uri) {
      uri = nextUri; document.uri = uri;
      await transport.connection.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'java', version, text: model.getValue() } });
      return;
    }
    await transport.connection.sendNotification('textDocument/didChange', { textDocument: { uri, version }, contentChanges: [{ text: model.getValue() }] });
  };
  const request = async <T>(method: string, params: unknown, token?: Monaco.CancellationToken): Promise<T | undefined> => {
    if (!ready || !transport || pending >= 8 || token?.isCancellationRequested) return undefined;
    const connection = transport.connection;
    const cancel = new CancellationTokenSource();
    const subscription = token?.onCancellationRequested(() => cancel.cancel());
    let timeout: ReturnType<typeof setTimeout> | undefined;
    let stalled: ReturnType<typeof setTimeout> | undefined;
    const version = model.getVersionId();
    // Monaco retains a completion session while its word grows, and resolves imports
    // after inserting the primary edit. Its cancellation token owns those lifetimes.
    const completionRequest = method === 'textDocument/completion' || method === 'completionItem/resolve';
    let requestUri = uri;
    let release: (() => void) | undefined;
    try {
      if (method !== 'completionItem/resolve') await synchronize();
      requestUri = uri;
      if (cancel.token.isCancellationRequested || transport?.connection !== connection || (!completionRequest && model.getVersionId() !== version) || pending >= 8) return undefined;
      pending++;
      activeRequests.set(cancel, completionRequest);
      let released = false;
      release = () => {
        if (released) return;
        released = true; pending--; activeRequests.delete(cancel);
        clearTimeout(stalled); cancel.dispose();
      };
      // A cancelled UI request stops waiting immediately, but still counts toward the
      // transport limit until the server acknowledges it (or the connection closes).
      const response = connection.sendRequest<T>(method, params, cancel.token);
      void response.then(release, release);
      stalled = setTimeout(() => { if (transport?.connection === connection) fail('语义分析长时间未响应，请重新连接'); }, 60_000);
      const result = await Promise.race([
        response,
        new Promise<undefined>((resolve) => cancel.token.onCancellationRequested(() => resolve(undefined))),
        new Promise<undefined>((resolve) => { timeout = setTimeout(() => {
          cancel.cancel();
          // Slow analysis must not tear down an otherwise healthy, warmed-up session.
          resolve(undefined);
        }, 15_000); }),
      ]);
      if (result !== undefined && transport?.connection === connection) attempts = 0;
      return !cancel.token.isCancellationRequested && transport?.connection === connection && !model.isDisposed()
        && (completionRequest ? uri === requestUri : model.getVersionId() === version) ? result : undefined;
    } catch { release?.(); return undefined; }
    finally { clearTimeout(timeout); subscription?.dispose(); if (!release) cancel.dispose(); }
  };

  async function start() {
    if (connecting || controller.signal.aborted) return;
    connecting = true;
    attempts++;
    ready = false;
    transport?.close(); transport = undefined;
    status({ ready: false, message: 'Java 语义提示初始化中…', retryable: false });
    let initializationTimeout: ReturnType<typeof setTimeout> | undefined;
    let next: JavaLanguageTransport | undefined;
    let failureMessage = '无法建立语义连接，请检查登录状态或网络';
    try {
      next = await connectJavaLanguage(taskId, sessionId, controller.signal);
      if (controller.signal.aborted) { next.close(); return; }
      transport = next;
      failureMessage = 'Java 语义服务初始化失败，请重新连接';
      const failCurrent = () => { if (transport === next) fail(next?.failureMessage); };
      next.connection.onClose(failCurrent);
      next.connection.onError(failCurrent);
      let initialDiagnostics: Lsp.PublishDiagnosticsParams | undefined;
      const publishDiagnostics = (params: Lsp.PublishDiagnosticsParams) => {
        if (model.isDisposed() || params.uri !== uri || sentVersion !== model.getVersionId()) return;
        // JDT LS 1.60 publishes diagnostics without the optional LSP version field.
        // Reject explicitly stale versions and unsynchronized edits; never drop all versionless diagnostics.
        if (params.version !== undefined && params.version !== model.getVersionId()) return;
        if (!ready) { initialDiagnostics = params; return; }
        monaco.editor.setModelMarkers(model, 'datascalpel-jdt', params.diagnostics.map((item) => ({
          ...range(item.range), severity: item.severity === 1 ? monaco.MarkerSeverity.Error : item.severity === 2 ? monaco.MarkerSeverity.Warning : monaco.MarkerSeverity.Info,
          message: item.message, source: 'Java', code: item.code === undefined ? undefined : String(item.code),
        })));
      };
      next.connection.onNotification('textDocument/publishDiagnostics', publishDiagnostics);
      next.connection.onNotification('datascalpel/status', (value: { code?: string }) => {
        if (transport !== next) return;
        fail(value?.code === 'JAVA_LANGUAGE_EXITED' ? 'Java 语义进程已退出，请重新连接'
          : 'Java 语义服务处理失败，请重新连接');
      });
      next.connection.listen();
      await Promise.race([next.connection.sendRequest('initialize', {}), new Promise<never>((_, reject) => {
        initializationTimeout = setTimeout(() => reject(new Error('Initialization timeout')), 30_000);
      })]);
      if (controller.signal.aborted || transport !== next) return;
      clearTimeout(initializationTimeout);
      await next.connection.sendNotification('initialized', {});
      status({ ready: false, message: '正在加载 Java、Spark 与 SDK 类型索引…', retryable: false });
      failureMessage = 'Java 类型索引准备失败，请重试';
      await prepareJavaLanguage(next.connection, controller.signal);
      if (controller.signal.aborted || transport !== next) return;
      uri = javaEntry(monaco, model.getValue(), fileName).uri; document.uri = uri;
      sentVersion = model.getVersionId();
      failureMessage = '当前文档分析未完成，请重试';
      await next.connection.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'java', version: sentVersion, text: model.getValue() } });
      await Promise.race([next.connection.sendRequest('textDocument/hover', { textDocument: document, position: { line: 0, character: 0 } }),
        new Promise<never>((_, reject) => { clearTimeout(initializationTimeout); initializationTimeout = setTimeout(() => reject(new Error('Document preparation timeout')), 15_000); })]);
      if (controller.signal.aborted || transport !== next) return;
      ready = true;
      preparedOnce = true;
      // Reset only after actual type probes and current-document analysis succeed,
      // not merely after the socket/initialize handshake. Idle sessions must recover too.
      attempts = 0;
      if (initialDiagnostics) publishDiagnostics(initialDiagnostics);
      status({ ready: true, message: 'Java 语义提示已就绪', retryable: false });
    } catch { if (!next || transport === next) fail(failureMessage); }
    finally {
      clearTimeout(initializationTimeout); connecting = false;
      if (!transport) scheduleRetry();
    }
  }

  const originals = new WeakMap<Monaco.languages.CompletionItem, { item: Lsp.CompletionItem; uri: string }>();
  const completion = (item: Lsp.CompletionItem, fallback: Monaco.IRange): Monaco.languages.CompletionItem => ({
    label: item.label,
    kind: completionKind(item.kind),
    detail: item.detail, documentation: documentation(item.documentation), filterText: item.filterText, sortText: item.sortText,
    insertText: item.textEdit?.newText ?? item.insertText ?? item.label,
    insertTextRules: item.insertTextFormat === 2 ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
    range: item.textEdit ? ('range' in item.textEdit ? range(item.textEdit.range) : { insert: range(item.textEdit.insert), replace: range(item.textEdit.replace) }) : fallback,
    additionalTextEdits: item.additionalTextEdits?.map((edit) => ({ range: range(edit.range), text: edit.newText })),
    commitCharacters: item.commitCharacters,
  });
  function completionKind(kind: Lsp.CompletionItemKind | undefined): Monaco.languages.CompletionItemKind {
    const k = monaco.languages.CompletionItemKind;
    const mapping = [k.Text, k.Method, k.Function, k.Constructor, k.Field, k.Variable, k.Class,
      k.Interface, k.Module, k.Property, k.Unit, k.Value, k.Enum, k.Keyword, k.Snippet, k.Color,
      k.File, k.Reference, k.Folder, k.EnumMember, k.Constant, k.Struct, k.Event, k.Operator, k.TypeParameter];
    return kind ? mapping[kind - 1] ?? k.Text : k.Text;
  }
  disposables.push(monaco.languages.registerCompletionItemProvider('java', {
    triggerCharacters: ['.', '@', '#', '*', ' '],
    async provideCompletionItems(current, cursor, context, token) {
      if (!owns(current) || token.isCancellationRequested) return { suggestions: [] };
      const sequence = ++completionSequence;
      const word = current.getWordUntilPosition(cursor);
      const fallback = new monaco.Range(cursor.lineNumber, word.startColumn, cursor.lineNumber, word.endColumn);
      const params = {
        textDocument: document, position: position(cursor), context: { triggerKind: context.triggerKind + 1, triggerCharacter: context.triggerCharacter },
      };
      let result: Lsp.CompletionItem[] | Lsp.CompletionList | null | undefined;
      for (let attempt = 0; attempt < 3; attempt++) {
        result = await request<Lsp.CompletionItem[] | Lsp.CompletionList | null>('textDocument/completion', params, token);
        if (sequence !== completionSequence || current.isDisposed() || token.isCancellationRequested) return { suggestions: [] };
        if (!result || Array.isArray(result) || result.items.length || !result.isIncomplete) break;
        // During first indexing JDT can return an empty, explicitly incomplete list.
        // Retry only that case, at most twice; never retry a genuine empty result.
        status({ ready: true, message: 'Java 类型索引准备中，正在重试补全…', retryable: false });
      }
      if (sequence !== completionSequence || current.isDisposed()) return { suggestions: [] };
      const items = Array.isArray(result) ? result : result?.items ?? [];
      if (items.length) status({ ready: true, message: 'Java 语义提示已连接', retryable: false });
      else if (result && !Array.isArray(result) && result.isIncomplete) status({ ready: true, message: 'Java 类型索引仍在准备，可继续输入或按 Ctrl+Space 重试', retryable: false });
      return { incomplete: !Array.isArray(result) && Boolean(result?.isIncomplete), suggestions: items.map((item) => {
        const value = completion(item, fallback); originals.set(value, { item, uri }); return value;
      }) };
    },
    async resolveCompletionItem(item, token) {
      const original = originals.get(item);
      if (!original || uri !== original.uri) return item;
      const resolved = await request<Lsp.CompletionItem>('completionItem/resolve', original.item, token);
      return resolved ? { ...item, ...completion(resolved, 'insert' in item.range ? item.range.insert : item.range) } : item;
    },
  }));
  disposables.push(monaco.languages.registerHoverProvider('java', {
    async provideHover(current, cursor, token) {
      if (!owns(current)) return null;
      const result = await request<Lsp.Hover | null>('textDocument/hover', { textDocument: document, position: position(cursor) }, token);
      if (!result) return null;
      const contents = Array.isArray(result.contents) ? result.contents : [result.contents];
      return { range: result.range ? range(result.range) : undefined, contents: contents.map((value) => ({
        value: typeof value === 'string' ? value : 'language' in value ? `\`\`\`${value.language}\n${value.value}\n\`\`\`` : value.value,
        isTrusted: false, supportHtml: false,
      })) };
    },
  }));
  disposables.push(monaco.languages.registerSignatureHelpProvider('java', {
    signatureHelpTriggerCharacters: ['(', ','], signatureHelpRetriggerCharacters: [')'],
    async provideSignatureHelp(current, cursor, token) {
      if (!owns(current)) return null;
      const result = await request<Lsp.SignatureHelp | null>('textDocument/signatureHelp', { textDocument: document, position: position(cursor) }, token);
      if (!result) return null;
      return { value: { activeSignature: result.activeSignature ?? 0, activeParameter: result.activeParameter ?? 0,
        signatures: result.signatures.map((item) => ({ label: item.label, documentation: documentation(item.documentation),
          parameters: (item.parameters ?? []).map((parameter) => ({ label: parameter.label, documentation: documentation(parameter.documentation) })) })) }, dispose() {} };
    },
  }));
  disposables.push(monaco.languages.registerDocumentFormattingEditProvider('java', {
    async provideDocumentFormattingEdits(current, options, token) {
      if (!owns(current)) return [];
      return (await request<Lsp.TextEdit[]>('textDocument/formatting', { textDocument: document, options }, token))?.map((edit) => ({ range: range(edit.range), text: edit.newText })) ?? [];
    },
  }));
  const rename = async (cursor: Monaco.Position, newName: string, token?: Monaco.CancellationToken): Promise<Monaco.languages.WorkspaceEdit & { rejectReason?: string }> => {
      if (!ready) return { edits: [], rejectReason: 'Java 语义服务尚未就绪' };
      if (!/^[A-Za-z_$][\w$]*$/.test(newName)) return { edits: [], rejectReason: '请输入有效的 Java 名称' };
      const version = model.getVersionId();
      const source = model.getValue();
      const result = await request<Lsp.WorkspaceEdit | null>('textDocument/rename', { textDocument: document, position: position(cursor), newName }, token);
      if (!result) return { edits: [], rejectReason: '未取得重命名结果，请重试；若代码已变化，请重新确认' };
      const edits: Monaco.languages.IWorkspaceTextEdit[] = [];
      const add = (target: string, changes: Lsp.TextEdit[]) => {
        if (target === uri) changes.forEach((edit) => edits.push({ resource: model.uri, versionId: model.getVersionId(), textEdit: { range: range(edit.range), text: edit.newText } }));
      };
      Object.entries(result.changes ?? {}).forEach(([target, changes]) => add(target, changes));
      result.documentChanges?.forEach((change) => { if ('textDocument' in change) add(change.textDocument.uri, change.edits); });
      // Physical RenameFile operations are not applied in the browser. Source sync updates the managed filename.
      if (!edits.length && !result.documentChanges?.length && !Object.values(result.changes ?? {}).some((changes) => changes.length)
        && /import\s+static\s+org\.apache\.spark\.sql\.functions\.\s*\*\s*;/.test(source)) {
        const entry = javaEntry(monaco, source, fileName);
        const name = entry.fileName.replace(/\.java$/, '');
        const cursorOffset = model.getOffsetAt(cursor);
        // Do not turn arbitrary symbol renames or explicit errors into text replacement.
        if (entry.offset >= 0 && cursorOffset >= entry.offset && cursorOffset < entry.offset + name.length && newName !== name) {
          const current = () => !controller.signal.aborted && !model.isDisposed() && model.getVersionId() === version && !token?.isCancellationRequested;
          try {
            const [highlights, symbols] = await Promise.all([
              request<Lsp.DocumentHighlight[] | null>('textDocument/documentHighlight', { textDocument: document, position: position(cursor) }, token),
              request<Lsp.DocumentSymbol[] | null>('textDocument/documentSymbol', { textDocument: document }, token),
            ]);
            if (!current() || !highlights?.length || !symbols?.length) throw new Error('未取得代码引用，源码未改变，请重试');
            const candidate = entryRenameCandidate(source, visibleJavaSource(monaco, source), entry.offset, name, newName, highlights, symbols);
            // Existing read-only compiler: no draft save, JAR replacement, execution or import cleanup.
            const checked = await checkSparkJarOnlineSource(taskId, candidate.source);
            if (!current()) throw new Error('代码已变化，本次重命名未应用，请重新确认');
            if (checked.status !== 'SUCCEEDED') throw new Error('改名后的代码检查未通过，源码未改变；请先检查代码或换一个名称');
            return { edits: candidate.edits.map((textEdit) => ({ resource: model.uri, versionId: version, textEdit })) };
          } catch (error) {
            return { edits: [], rejectReason: error instanceof Error ? error.message : '重命名未完成，源码未改变，请重试' };
          }
        }
      }
      return edits.length ? { edits } : { edits: [], rejectReason: '语义服务未返回当前文件的重命名修改，源码未改变；请重试或查看代码诊断' };
  };
  disposables.push(monaco.languages.registerRenameProvider('java', {
    provideRenameEdits: (current, cursor, newName, token) => owns(current) ? rename(cursor, newName, token) : { edits: [], rejectReason: 'Java 语义服务尚未就绪' },
  }));
  disposables.push(model.onDidChangeContent(() => {
    activeRequests.forEach((completionRequest, cancel) => { if (!completionRequest) cancel.cancel(); });
    monaco.editor.setModelMarkers(model, 'datascalpel-jdt', []);
    clearTimeout(changeTimer);
    changeTimer = setTimeout(() => { void synchronize().catch(() => fail('代码同步失败，请重新连接')); }, 200);
  }));
  void start();
  return {
    get ready() { return ready; },
    rename,
    retry: () => { attempts = 0; clearTimeout(retryTimer); retryTimer = undefined; void start(); },
    dispose: () => {
      activeRequests.forEach((_completionRequest, cancel) => cancel.cancel());
      controller.abort(); clearTimeout(retryTimer); clearTimeout(changeTimer);
      disposables.forEach((item) => item.dispose()); transport?.close();
      if (!model.isDisposed()) monaco.editor.setModelMarkers(model, 'datascalpel-jdt', []);
    },
  };
}
