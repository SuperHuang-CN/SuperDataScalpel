import type { MessageConnection } from 'vscode-jsonrpc/browser';
import { CancellationTokenSource } from 'vscode-jsonrpc/browser';
import type { CompletionItem, CompletionList } from 'vscode-languageserver-protocol';

/** Analyze a disposable document in this session, never execute or save user code. */
export async function prepareJavaLanguage(connection: MessageConnection, signal: AbortSignal) {
  const uri = 'file:///datascalpel/src/__DataScalpelLanguageProbe.java';
  const lines = ['public class __DataScalpelLanguageProbe {',
    ' void types() { new Strin; }',
    ' void sdk(cn.superhuang.datascalpel.sdk.SparkJobContext context) { context.mod; }', '}'];
  const cancel = new CancellationTokenSource();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let abort: () => void = () => undefined;
  const stopped = new Promise<never>((_resolve, reject) => {
    abort = () => { cancel.cancel(); reject(new Error('Java preparation stopped')); };
    signal.addEventListener('abort', abort, { once: true });
    timer = setTimeout(abort, 60_000);
  });
  try {
    if (signal.aborted) throw new Error('Java preparation stopped');
    await Promise.race([stopped, connection.sendNotification('textDocument/didOpen', { textDocument: { uri, languageId: 'java', version: 0, text: lines.join('\n') } })]);
    for (const probe of [{ line: 1, prefix: 'new Strin', expected: 'String(' }, { line: 2, prefix: 'context.mod', expected: 'models(' }]) {
      let found = false;
      for (let attempt = 0; attempt < 12 && !found; attempt++) {
        const result = await Promise.race([stopped, connection.sendRequest<CompletionList | CompletionItem[] | null>('textDocument/completion', {
          textDocument: { uri }, position: { line: probe.line, character: lines[probe.line].indexOf(probe.prefix) + probe.prefix.length },
          context: { triggerKind: 1 },
        }, cancel.token)]);
        const items = Array.isArray(result) ? result : result?.items ?? [];
        found = items.some((item) => item.label.startsWith(probe.expected));
        if (!found && (Array.isArray(result) || result && !result.isIncomplete)) throw new Error('Java analysis dependencies unavailable');
      }
      if (!found) throw new Error('Java indexing did not become ready');
    }
  } finally {
    clearTimeout(timer); signal.removeEventListener('abort', abort); cancel.dispose();
  }
}
