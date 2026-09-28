import type * as Monaco from 'monaco-editor';
import type { DocumentHighlight, DocumentSymbol } from 'vscode-languageserver-protocol';

/** Narrow fallback for JDT's Spark wildcard-import rename failure. Only semantic ranges are edited. */
export function entryRenameCandidate(source: string, visible: string, entryOffset: number, oldName: string,
  newName: string, highlights: DocumentHighlight[], symbols: DocumentSymbol[]): { source: string; edits: Monaco.languages.TextEdit[] } {
  if (visible.match(/[\p{L}\p{N}_$]+/gu)?.includes(newName)) {
    throw new Error('该名称已在代码中使用，请换一个名称');
  }
  const lines = source.split('\n');
  const offsets: number[] = [];
  let offset = 0;
  lines.forEach((line) => { offsets.push(offset); offset += line.length + 1; });
  const entry = symbols.find((symbol) => symbol.kind === 5 && symbol.name === oldName && symbol.selectionRange
    && offsets[symbol.selectionRange.start.line] + symbol.selectionRange.start.character === entryOffset);
  if (!entry) throw new Error('未能准确定位主类，源码未改变，请重试');
  // OccurrencesFinder identifies type references but excludes constructor declarations.
  const constructors = (entry.children ?? []).filter((symbol) => symbol.kind === 9).map((symbol) => ({ range: symbol.selectionRange }));
  const spans = [...highlights, ...constructors].map(({ range: { start, end } }) => {
    const line = lines[start.line];
    if (!Number.isInteger(start.line) || !Number.isInteger(start.character) || !Number.isInteger(end.character)
      || line === undefined || start.line !== end.line || start.character < 0 || end.character > line.length
      || line.slice(start.character, end.character) !== oldName) {
      throw new Error('未能准确定位代码引用，源码未改变，请重试');
    }
    return { start: offsets[start.line] + start.character, end: offsets[start.line] + end.character,
      edit: { range: { startLineNumber: start.line + 1, startColumn: start.character + 1,
        endLineNumber: end.line + 1, endColumn: end.character + 1 }, text: newName } };
  }).sort((a, b) => a.start - b.start);
  if (!spans.some((span) => span.start === entryOffset)
    || spans.some((span, index) => index > 0 && span.start < spans[index - 1].end)) {
    throw new Error('未能准确定位主类，源码未改变，请重试');
  }
  let candidate = source;
  for (const span of [...spans].reverse()) candidate = candidate.slice(0, span.start) + newName + candidate.slice(span.end);
  return { source: candidate, edits: spans.map((span) => span.edit) };
}
